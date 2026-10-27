package de.pwe.schiffeversenken.domain;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.List;
import java.util.Optional;
import java.util.function.Consumer;
import java.util.stream.Stream;

import static de.pwe.schiffeversenken.domain.PlayerSide.A;
import static de.pwe.schiffeversenken.domain.PlayerSide.B;
import static de.pwe.schiffeversenken.domain.TestFleets.horizontal;
import static de.pwe.schiffeversenken.domain.TestFleets.rowLayout;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class GameTest {

    /** Kleines Feld: ein Zweier pro Spieler, sinkt nach zwei Treffern. */
    private static final RuleSet ONE_SHIP = new RuleSet(4, 4, List.of(2), false, false);
    private static final List<ShipPlacement> ONE_SHIP_FLEET = List.of(horizontal(0, 0, 2));

    private static final Coordinate WATER = new Coordinate(9, 9);
    private static final Coordinate SHIP_CELL = new Coordinate(0, 0);

    // --- Phasen ---

    @Test
    void newGameWaitsForSecondPlayer() {
        Game game = Game.create(RuleSet.standard());

        assertEquals(GamePhase.WAITING, game.phase());
        assertEquals(RuleSet.standard(), game.rules());
        assertEquals(Optional.empty(), game.currentTurn());
        assertEquals(Optional.empty(), game.winner());
    }

    @Test
    void joiningStartsPlacement() {
        Game game = Game.create(RuleSet.standard());

        game.join();

        assertEquals(GamePhase.PLACEMENT, game.phase());
        assertFalse(game.isFleetReady(A));
        assertFalse(game.isFleetReady(B));
    }

    @Test
    void joiningTwiceIsRejected() {
        Game game = Game.create(RuleSet.standard());
        game.join();

        assertViolation(Violation.WRONG_PHASE, game::join);
        assertEquals(GamePhase.PLACEMENT, game.phase());
    }

    @Test
    void placingFleetBeforeOpponentJoinedIsRejected() {
        Game game = Game.create(RuleSet.standard());

        assertViolation(Violation.WRONG_PHASE, () -> game.placeFleet(A, rowLayout(RuleSet.standard())));
        assertFalse(game.isFleetReady(A));
    }

    @ParameterizedTest
    @EnumSource(PlayerSide.class)
    void gameKeepsWaitingForOpponentAfterOneFleetIsPlaced(PlayerSide side) {
        Game game = placementGame(RuleSet.standard());

        game.placeFleet(side, rowLayout(RuleSet.standard()));

        assertEquals(GamePhase.PLACEMENT, game.phase());
        assertTrue(game.isFleetReady(side));
        assertFalse(game.isFleetReady(side.opponent()));
    }

    @ParameterizedTest
    @MethodSource("de.pwe.schiffeversenken.domain.TestFleets#ruleVariants")
    void gameStartsWithPlayerAWhenBothFleetsArePlaced(RuleSet rules) {
        Game game = placementGame(rules);

        game.placeFleet(B, rowLayout(rules));
        game.placeFleet(A, rowLayout(rules));

        assertEquals(GamePhase.RUNNING, game.phase());
        assertEquals(Optional.of(A), game.currentTurn());
    }

    @Test
    void placingFleetTwiceIsRejected() {
        Game game = placementGame(RuleSet.standard());
        game.placeFleet(A, rowLayout(RuleSet.standard()));

        assertViolation(Violation.FLEET_ALREADY_PLACED, () -> game.placeFleet(A, rowLayout(RuleSet.standard())));
    }

    @Test
    void invalidFleetIsRejectedAndPlayerStaysNotReady() {
        Game game = placementGame(RuleSet.standard());

        assertViolation(Violation.FLEET_MISMATCH, () -> game.placeFleet(A, List.of()));
        assertFalse(game.isFleetReady(A));
        assertEquals(GamePhase.PLACEMENT, game.phase());
    }

    // --- Schüsse und Rundenwechsel ---

    static Stream<Arguments> phasesWithoutShooting() {
        return Stream.of(
                Arguments.of("WAITING", (Consumer<Game>) game -> { }),
                Arguments.of("PLACEMENT", (Consumer<Game>) Game::join),
                Arguments.of("ABANDONED", (Consumer<Game>) Game::abandon));
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("phasesWithoutShooting")
    void shootingOutsideRunningPhaseIsRejected(String phase, Consumer<Game> setup) {
        Game game = Game.create(RuleSet.standard());
        setup.accept(game);

        assertViolation(Violation.WRONG_PHASE, () -> game.fire(A, SHIP_CELL));
    }

    @Test
    void shootingOutOfTurnIsRejected() {
        Game game = runningGame(RuleSet.standard());

        assertViolation(Violation.NOT_YOUR_TURN, () -> game.fire(B, SHIP_CELL));
        assertEquals(Optional.of(A), game.currentTurn());
    }

    @Test
    void shotHitsTheOpponentsBoard() {
        RuleSet rules = RuleSet.standard();
        Game game = placementGame(rules);
        game.placeFleet(A, List.of(
                horizontal(5, 0, 5), horizontal(5, 2, 4), horizontal(5, 4, 3), horizontal(5, 6, 2)));
        game.placeFleet(B, rowLayout(rules));

        assertEquals(ShotResult.HIT, game.fire(A, SHIP_CELL).result());
    }

    @ParameterizedTest
    @ValueSource(booleans = {true, false})
    void missAlwaysPassesTheTurn(boolean shootAgainOnHit) {
        Game game = runningGame(new RuleSet(10, 10, List.of(5, 4, 3, 2), shootAgainOnHit, false));

        assertEquals(ShotResult.MISS, game.fire(A, WATER).result());

        assertEquals(Optional.of(B), game.currentTurn());
    }

    @ParameterizedTest
    @ValueSource(booleans = {true, false})
    void hitPassesTheTurnUnlessShootAgainOnHit(boolean shootAgainOnHit) {
        Game game = runningGame(new RuleSet(10, 10, List.of(5, 4, 3, 2), shootAgainOnHit, false));

        assertEquals(ShotResult.HIT, game.fire(A, SHIP_CELL).result());

        assertEquals(Optional.of(shootAgainOnHit ? A : B), game.currentTurn());
    }

    @ParameterizedTest
    @ValueSource(booleans = {true, false})
    void sinkingPassesTheTurnUnlessShootAgainOnHit(boolean shootAgainOnHit) {
        RuleSet rules = new RuleSet(5, 5, List.of(2, 2), shootAgainOnHit, false);
        Game game = runningGame(rules, List.of(horizontal(0, 0, 2), horizontal(3, 4, 2)));
        game.fire(A, new Coordinate(0, 0));
        if (!shootAgainOnHit) {
            game.fire(B, new Coordinate(2, 2));
        }

        assertEquals(ShotResult.SUNK, game.fire(A, new Coordinate(1, 0)).result());

        assertEquals(Optional.of(shootAgainOnHit ? A : B), game.currentTurn());
    }

    @Test
    void shotOnAlreadyShotCellIsRejectedAndTurnIsKept() {
        Game game = runningGame(RuleSet.standard());
        game.fire(A, WATER);
        game.fire(B, WATER);

        assertViolation(Violation.CELL_ALREADY_SHOT, () -> game.fire(A, WATER));
        assertEquals(Optional.of(A), game.currentTurn());
    }

    @ParameterizedTest
    @EnumSource(PlayerSide.class)
    void sinkingAllShipsFinishesTheGame(PlayerSide winner) {
        Game game = runningGame(ONE_SHIP, ONE_SHIP_FLEET);
        if (winner == B) {
            game.fire(A, new Coordinate(3, 3));
        }
        game.fire(winner, new Coordinate(0, 0));
        game.fire(winner.opponent(), new Coordinate(3, 2));

        ShotOutcome outcome = game.fire(winner, new Coordinate(1, 0));

        assertEquals(ShotResult.WIN, outcome.result());
        assertEquals(GamePhase.FINISHED, game.phase());
        assertEquals(Optional.of(winner), game.winner());
        assertEquals(Optional.empty(), game.currentTurn());
    }

    @Test
    void shootingAfterTheGameIsFinishedIsRejected() {
        Game game = finishedGameWonByA();

        assertViolation(Violation.WRONG_PHASE, () -> game.fire(B, new Coordinate(3, 3)));
    }

    // --- Aufgeben und Verwaisen ---

    static Stream<Arguments> resignablePhases() {
        return Stream.of(
                Arguments.of(GamePhase.PLACEMENT, A),
                Arguments.of(GamePhase.PLACEMENT, B),
                Arguments.of(GamePhase.RUNNING, A),
                Arguments.of(GamePhase.RUNNING, B));
    }

    @ParameterizedTest
    @MethodSource("resignablePhases")
    void resigningLetsTheOpponentWin(GamePhase phase, PlayerSide resigning) {
        Game game = phase == GamePhase.RUNNING ? runningGame(RuleSet.standard()) : placementGame(RuleSet.standard());

        game.resign(resigning);

        assertEquals(GamePhase.FINISHED, game.phase());
        assertEquals(Optional.of(resigning.opponent()), game.winner());
        assertEquals(Optional.empty(), game.currentTurn());
    }

    @Test
    void resigningWhileWaitingIsRejected() {
        Game game = Game.create(RuleSet.standard());

        assertViolation(Violation.WRONG_PHASE, () -> game.resign(A));
        assertEquals(GamePhase.WAITING, game.phase());
    }

    @Test
    void resigningAFinishedGameIsRejected() {
        Game game = finishedGameWonByA();

        assertViolation(Violation.WRONG_PHASE, () -> game.resign(A));
        assertEquals(Optional.of(A), game.winner());
    }

    static Stream<Arguments> unfinishedGames() {
        return Stream.of(
                Arguments.of("WAITING", Game.create(RuleSet.standard())),
                Arguments.of("PLACEMENT", placementGame(RuleSet.standard())),
                Arguments.of("RUNNING", runningGame(RuleSet.standard())));
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("unfinishedGames")
    void unfinishedGameCanBeAbandoned(String phase, Game game) {
        game.abandon();

        assertEquals(GamePhase.ABANDONED, game.phase());
        assertEquals(Optional.empty(), game.winner());
        assertEquals(Optional.empty(), game.currentTurn());
    }

    @Test
    void finishedGameCannotBeAbandoned() {
        Game game = finishedGameWonByA();

        assertViolation(Violation.WRONG_PHASE, game::abandon);
        assertEquals(GamePhase.FINISHED, game.phase());
    }

    // --- Hilfsmethoden ---

    private static Game placementGame(RuleSet rules) {
        Game game = Game.create(rules);
        game.join();
        return game;
    }

    private static Game runningGame(RuleSet rules) {
        return runningGame(rules, rowLayout(rules));
    }

    private static Game runningGame(RuleSet rules, List<ShipPlacement> fleet) {
        Game game = placementGame(rules);
        game.placeFleet(A, fleet);
        game.placeFleet(B, fleet);
        return game;
    }

    private static Game finishedGameWonByA() {
        Game game = runningGame(ONE_SHIP, ONE_SHIP_FLEET);
        game.fire(A, new Coordinate(0, 0));
        game.fire(B, new Coordinate(3, 3));
        game.fire(A, new Coordinate(1, 0));
        return game;
    }

    private static void assertViolation(Violation expected, Runnable action) {
        RuleViolationException exception = assertThrows(RuleViolationException.class, action::run);
        assertEquals(expected, exception.violation());
    }
}
