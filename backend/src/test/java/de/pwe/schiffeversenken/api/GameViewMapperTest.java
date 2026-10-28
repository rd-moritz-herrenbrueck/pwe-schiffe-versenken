package de.pwe.schiffeversenken.api;

import de.pwe.schiffeversenken.api.dto.BoardView;
import de.pwe.schiffeversenken.api.dto.GameResult;
import de.pwe.schiffeversenken.api.dto.GameView;
import de.pwe.schiffeversenken.api.dto.RuleSetDto;
import de.pwe.schiffeversenken.api.dto.ShipView;
import de.pwe.schiffeversenken.api.dto.ShotView;
import de.pwe.schiffeversenken.domain.Coordinate;
import de.pwe.schiffeversenken.domain.Game;
import de.pwe.schiffeversenken.domain.GamePhase;
import de.pwe.schiffeversenken.domain.Orientation;
import de.pwe.schiffeversenken.domain.PlayerSide;
import de.pwe.schiffeversenken.domain.RuleSet;
import de.pwe.schiffeversenken.domain.ShipPlacement;
import de.pwe.schiffeversenken.domain.ShotOutcome;
import de.pwe.schiffeversenken.domain.ShotResult;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Stream;

import static de.pwe.schiffeversenken.domain.PlayerSide.A;
import static de.pwe.schiffeversenken.domain.PlayerSide.B;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Sicht eines Spielers auf das Spiel. Schwerpunkt Anti-Cheat: Gegnerische Schiffe bleiben verborgen, bis sie
 * versenkt sind.
 */
class GameViewMapperTest {

    private static final UUID GAME_ID = UUID.fromString("00000000-0000-0000-0000-000000000001");
    private static final RuleSet RULES = new RuleSet(6, 6, List.of(3, 2), false, false);
    private static final List<ShipPlacement> FLEET = List.of(horizontal(0, 0, 3), horizontal(0, 2, 2));
    private static final BoardView EMPTY_BOARD = new BoardView(List.of(), List.of());

    @Test
    void newLobbyShowsCreatorWaitingWithoutOpponent() {
        GameView view = GameViewMapper.toView(GAME_ID, A, null, Game.create(RULES));

        assertEquals(new GameView(GAME_ID, GamePhase.WAITING, A, null, RuleSetDto.from(RULES), false, false, false,
                null, EMPTY_BOARD, EMPTY_BOARD), view);
    }

    @Test
    void readinessIsShownFromTheViewersPerspective() {
        Game game = Game.create(RULES);
        game.join();
        game.placeFleet(A, FLEET);

        GameView viewOfA = GameViewMapper.toView(GAME_ID, A, "Bob", game);
        GameView viewOfB = GameViewMapper.toView(GAME_ID, B, "Ada", game);

        assertTrue(viewOfA.yourFleetReady());
        assertFalse(viewOfA.opponentFleetReady());
        assertFalse(viewOfB.yourFleetReady());
        assertTrue(viewOfB.opponentFleetReady());
        assertEquals("Bob", viewOfA.opponentNickname());
        assertEquals(EMPTY_BOARD, viewOfB.ownBoard());
        assertEquals(EMPTY_BOARD, viewOfB.enemyBoard());
    }

    @Test
    void onlyThePlayerWhoseTurnItIsMayShoot() {
        Game game = runningGame(RULES);

        assertTrue(GameViewMapper.toView(GAME_ID, A, "Bob", game).yourTurn());
        assertFalse(GameViewMapper.toView(GAME_ID, B, "Ada", game).yourTurn());
    }

    @Test
    void ownBoardShowsOwnFleetAndReceivedShots() {
        Game game = runningGame(RULES);
        game.fire(A, new Coordinate(5, 5));
        game.fire(B, new Coordinate(0, 0));
        game.fire(A, new Coordinate(5, 4));
        game.fire(B, new Coordinate(4, 4));

        BoardView ownBoard = GameViewMapper.toView(GAME_ID, A, "Bob", game).ownBoard();

        assertEquals(new BoardView(
                List.of(new ShipView(0, 0, 3, Orientation.HORIZONTAL, false),
                        new ShipView(0, 2, 2, Orientation.HORIZONTAL, false)),
                List.of(new ShotView(0, 0, true), new ShotView(4, 4, false))), ownBoard);
    }

    @Test
    void hitDoesNotRevealTheEnemyShip() {
        Game game = runningGame(RULES);
        game.fire(A, new Coordinate(0, 0));

        BoardView enemyBoard = GameViewMapper.toView(GAME_ID, A, "Bob", game).enemyBoard();

        assertEquals(new BoardView(List.of(), List.of(new ShotView(0, 0, true))), enemyBoard);
    }

    @Test
    void sunkEnemyShipIsRevealed() {
        Game game = runningGame(RULES);
        game.fire(A, new Coordinate(0, 0));
        game.fire(B, new Coordinate(5, 5));
        game.fire(A, new Coordinate(0, 2));
        game.fire(B, new Coordinate(5, 4));
        game.fire(A, new Coordinate(1, 2));

        BoardView enemyBoard = GameViewMapper.toView(GAME_ID, A, "Bob", game).enemyBoard();

        assertEquals(new BoardView(
                List.of(new ShipView(0, 2, 2, Orientation.HORIZONTAL, true)),
                List.of(new ShotView(0, 0, true), new ShotView(0, 2, true), new ShotView(1, 2, true))), enemyBoard);
    }

    @Test
    void finishedGameShowsResultFromTheViewersPerspective() {
        Game game = runningGame(RULES);
        game.resign(B);

        assertEquals(GameResult.WON, GameViewMapper.toView(GAME_ID, A, "Bob", game).result());
        assertEquals(GameResult.LOST, GameViewMapper.toView(GAME_ID, B, "Ada", game).result());
        assertFalse(GameViewMapper.toView(GAME_ID, A, "Bob", game).yourTurn());
    }

    @Test
    void abandonedGameHasNoResult() {
        Game game = runningGame(RULES);
        game.abandon();

        GameView view = GameViewMapper.toView(GAME_ID, A, "Bob", game);

        assertEquals(GamePhase.ABANDONED, view.phase());
        assertNull(view.result());
        assertFalse(view.yourTurn());
    }

    static Stream<RuleSet> ruleVariants() {
        return Stream.of(
                RuleSet.standard(),
                new RuleSet(10, 10, List.of(5, 4, 3, 2), true, false),
                new RuleSet(8, 8, List.of(4, 3, 2, 2), false, true),
                new RuleSet(12, 6, List.of(5, 4, 3), true, false));
    }

    /**
     * Beide Spieler schießen zeilenweise über das gegnerische Feld, bis das Spiel endet. Nach jedem Schuss gilt für
     * beide Sichten: Sichtbar sind genau die eigenen Schüsse mit korrektem Treffer-Flag und genau die vollständig
     * versenkten gegnerischen Schiffe.
     */
    @ParameterizedTest
    @MethodSource("ruleVariants")
    void enemyBoardRevealsNothingButOwnShotsAndSunkShips(RuleSet rules) {
        List<ShipPlacement> fleet = rowLayout(rules);
        Game game = runningGame(rules, fleet);
        Map<PlayerSide, List<ShotView>> shotsFired = new EnumMap<>(Map.of(A, new ArrayList<>(), B, new ArrayList<>()));
        Map<PlayerSide, Integer> nextCell = new EnumMap<>(Map.of(A, 0, B, 0));

        while (game.phase() == GamePhase.RUNNING) {
            PlayerSide shooter = game.currentTurn().orElseThrow();
            int index = nextCell.merge(shooter, 1, Integer::sum) - 1;
            Coordinate target = new Coordinate(index % rules.boardWidth(), index / rules.boardWidth());
            ShotOutcome outcome = game.fire(shooter, target);
            shotsFired.get(shooter).add(new ShotView(target.x(), target.y(), outcome.result() != ShotResult.MISS));

            for (PlayerSide viewer : PlayerSide.values()) {
                BoardView enemyBoard = GameViewMapper.toView(GAME_ID, viewer, "X", game).enemyBoard();
                List<ShotView> ownShots = shotsFired.get(viewer);
                assertEquals(ownShots, enemyBoard.shots());
                assertEquals(sunkShips(fleet, ownShots), enemyBoard.ships());
            }
        }
        assertEquals(GamePhase.FINISHED, game.phase());
    }

    // --- Hilfsmethoden ---

    /** Erwartete sichtbare Schiffe: alle Schiffe, deren Felder vollständig beschossen wurden, in Flottenreihenfolge. */
    private static List<ShipView> sunkShips(List<ShipPlacement> fleet, List<ShotView> shots) {
        Set<Coordinate> shotCells = new HashSet<>();
        shots.forEach(shot -> shotCells.add(new Coordinate(shot.x(), shot.y())));
        return fleet.stream()
                .filter(ship -> shotCells.containsAll(ship.cells()))
                .map(ship -> new ShipView(ship.start().x(), ship.start().y(), ship.length(), ship.orientation(), true))
                .toList();
    }

    private static Game runningGame(RuleSet rules) {
        return runningGame(rules, FLEET);
    }

    private static Game runningGame(RuleSet rules, List<ShipPlacement> fleet) {
        Game game = Game.create(rules);
        game.join();
        game.placeFleet(A, fleet);
        game.placeFleet(B, fleet);
        return game;
    }

    /** Schiff i liegt horizontal ab (0, 2*i). */
    private static List<ShipPlacement> rowLayout(RuleSet rules) {
        List<ShipPlacement> placements = new ArrayList<>();
        for (int i = 0; i < rules.fleet().size(); i++) {
            placements.add(horizontal(0, 2 * i, rules.fleet().get(i)));
        }
        return placements;
    }

    private static ShipPlacement horizontal(int x, int y, int length) {
        return new ShipPlacement(new Coordinate(x, y), length, Orientation.HORIZONTAL);
    }
}
