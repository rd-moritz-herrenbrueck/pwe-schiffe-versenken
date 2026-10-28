package de.pwe.schiffeversenken.domain;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

import static de.pwe.schiffeversenken.domain.PlayerSide.A;
import static de.pwe.schiffeversenken.domain.PlayerSide.B;
import static de.pwe.schiffeversenken.domain.TestFleets.horizontal;
import static de.pwe.schiffeversenken.domain.TestFleets.rowLayout;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * Speichern und Wiederherstellen eines Spiels: Ein wiederhergestelltes Spiel verhält sich wie das Original.
 */
class GameSnapshotTest {

    private static final RuleSet ONE_SHIP = new RuleSet(4, 4, List.of(2), false, false);
    private static final List<ShipPlacement> ONE_SHIP_FLEET = List.of(horizontal(0, 0, 2));
    private static final BoardSnapshot ONE_SHIP_BOARD = new BoardSnapshot(ONE_SHIP_FLEET, List.of());

    static Stream<Arguments> gamesInEveryPhase() {
        Game placementWithOneFleet = placementGame(RuleSet.standard());
        placementWithOneFleet.placeFleet(B, rowLayout(RuleSet.standard()));

        Game runningWithShots = runningGame(RuleSet.standard());
        runningWithShots.fire(A, new Coordinate(0, 0));
        runningWithShots.fire(B, new Coordinate(9, 9));

        Game resignedDuringPlacement = placementGame(RuleSet.standard());
        resignedDuringPlacement.placeFleet(A, rowLayout(RuleSet.standard()));
        resignedDuringPlacement.resign(A);

        Game abandoned = runningGame(RuleSet.standard());
        abandoned.abandon();

        return Stream.of(
                Arguments.of("WAITING", Game.create(RuleSet.standard())),
                Arguments.of("PLACEMENT ohne Flotten", placementGame(RuleSet.standard())),
                Arguments.of("PLACEMENT mit einer Flotte", placementWithOneFleet),
                Arguments.of("RUNNING mit Schüssen", runningWithShots),
                Arguments.of("FINISHED durch Sieg", finishedGameWonByA()),
                Arguments.of("FINISHED durch Aufgabe", resignedDuringPlacement),
                Arguments.of("ABANDONED", abandoned));
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("gamesInEveryPhase")
    void restoredGameHasTheSameStateAsTheOriginal(String phase, Game original) {
        Game restored = Game.restore(original.snapshot());

        assertEquals(original.snapshot(), restored.snapshot());
        assertEquals(original.phase(), restored.phase());
        assertEquals(original.currentTurn(), restored.currentTurn());
        assertEquals(original.winner(), restored.winner());
        assertEquals(original.rules(), restored.rules());
        assertEquals(original.isFleetReady(A), restored.isFleetReady(A));
        assertEquals(original.isFleetReady(B), restored.isFleetReady(B));
    }

    @Test
    void snapshotContainsPlacedFleetsAndShotsInOrder() {
        Game game = runningGame(ONE_SHIP, ONE_SHIP_FLEET);
        game.fire(A, new Coordinate(3, 3));
        game.fire(B, new Coordinate(0, 0));

        GameSnapshot snapshot = game.snapshot();

        assertEquals(new GameSnapshot(ONE_SHIP, GamePhase.RUNNING, A, null, Map.of(
                A, new BoardSnapshot(ONE_SHIP_FLEET, List.of(new Coordinate(0, 0))),
                B, new BoardSnapshot(ONE_SHIP_FLEET, List.of(new Coordinate(3, 3))))), snapshot);
    }

    @ParameterizedTest
    @MethodSource("de.pwe.schiffeversenken.domain.TestFleets#ruleVariants")
    void restoredGameContinuesLikeTheOriginal(RuleSet rules) {
        Game original = runningGame(rules);
        original.fire(A, new Coordinate(0, 0));
        Game restored = Game.restore(original.snapshot());

        PlayerSide next = original.currentTurn().orElseThrow();
        Coordinate target = new Coordinate(1, 0);

        assertEquals(original.fire(next, target), restored.fire(next, target));
        assertEquals(original.snapshot(), restored.snapshot());
    }

    @Test
    void restoredGameRemembersHitsOnShips() {
        Game original = runningGame(ONE_SHIP, ONE_SHIP_FLEET);
        original.fire(A, new Coordinate(0, 0));
        original.fire(B, new Coordinate(3, 3));

        Game restored = Game.restore(original.snapshot());

        assertEquals(ShotResult.WIN, restored.fire(A, new Coordinate(1, 0)).result());
        assertEquals(GamePhase.FINISHED, restored.phase());
    }

    @Test
    void restoredGameRejectsShotOnAlreadyShotCell() {
        Game original = runningGame(ONE_SHIP, ONE_SHIP_FLEET);
        original.fire(A, new Coordinate(3, 3));
        original.fire(B, new Coordinate(3, 3));

        Game restored = Game.restore(original.snapshot());

        RuleViolationException exception = assertThrows(RuleViolationException.class,
                () -> restored.fire(A, new Coordinate(3, 3)));
        assertEquals(Violation.CELL_ALREADY_SHOT, exception.violation());
    }

    @Test
    void snapshotIsNotChangedByLaterMoves() {
        Game game = runningGame(ONE_SHIP, ONE_SHIP_FLEET);
        GameSnapshot before = game.snapshot();

        game.fire(A, new Coordinate(0, 0));

        assertEquals(Map.of(A, ONE_SHIP_BOARD, B, ONE_SHIP_BOARD), before.boards());
        assertEquals(A, before.currentTurn());
    }

    static Stream<Arguments> inconsistentSnapshots() {
        Map<PlayerSide, BoardSnapshot> bothBoards = Map.of(A, ONE_SHIP_BOARD, B, ONE_SHIP_BOARD);
        return Stream.of(
                Arguments.of("WAITING mit Flotte", new GameSnapshot(ONE_SHIP, GamePhase.WAITING, null, null,
                        Map.of(A, ONE_SHIP_BOARD))),
                Arguments.of("PLACEMENT mit Zug", new GameSnapshot(ONE_SHIP, GamePhase.PLACEMENT, A, null,
                        Map.of())),
                Arguments.of("RUNNING mit nur einer Flotte", new GameSnapshot(ONE_SHIP, GamePhase.RUNNING, A, null,
                        Map.of(A, ONE_SHIP_BOARD))),
                Arguments.of("RUNNING ohne Zug", new GameSnapshot(ONE_SHIP, GamePhase.RUNNING, null, null,
                        bothBoards)),
                Arguments.of("RUNNING mit Sieger", new GameSnapshot(ONE_SHIP, GamePhase.RUNNING, A, B, bothBoards)),
                Arguments.of("FINISHED ohne Sieger", new GameSnapshot(ONE_SHIP, GamePhase.FINISHED, null, null,
                        bothBoards)),
                Arguments.of("ABANDONED mit Sieger", new GameSnapshot(ONE_SHIP, GamePhase.ABANDONED, null, A,
                        bothBoards)));
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("inconsistentSnapshots")
    void inconsistentSnapshotIsRejected(String description, GameSnapshot snapshot) {
        assertThrows(IllegalArgumentException.class, () -> Game.restore(snapshot));
    }

    @Test
    void snapshotWithInvalidFleetIsRejected() {
        BoardSnapshot overlapping = new BoardSnapshot(List.of(horizontal(0, 0, 2), horizontal(1, 0, 2)), List.of());
        RuleSet twoShips = new RuleSet(4, 4, List.of(2, 2), false, true);

        RuleViolationException exception = assertThrows(RuleViolationException.class, () -> Game.restore(
                new GameSnapshot(twoShips, GamePhase.PLACEMENT, null, null, Map.of(A, overlapping))));
        assertEquals(Violation.SHIPS_OVERLAP, exception.violation());
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
}
