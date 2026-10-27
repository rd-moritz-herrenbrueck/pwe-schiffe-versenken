package de.pwe.schiffeversenken.domain;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Stream;

import static de.pwe.schiffeversenken.domain.TestFleets.horizontal;
import static de.pwe.schiffeversenken.domain.TestFleets.rowLayout;
import static de.pwe.schiffeversenken.domain.TestFleets.vertical;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BoardShotTest {

    private static final RuleSet RULES = new RuleSet(5, 5, List.of(2, 2), false, false);
    private static final ShipPlacement TWO_SHIP = horizontal(0, 0, 2);
    private static final ShipPlacement SECOND_SHIP = vertical(4, 3, 2);

    private final Board board = Board.withFleet(RULES, List.of(TWO_SHIP, SECOND_SHIP));

    @Test
    void shotIntoWaterIsMiss() {
        ShotOutcome outcome = board.receiveShot(new Coordinate(2, 2));

        assertEquals(new ShotOutcome(new Coordinate(2, 2), ShotResult.MISS, Optional.empty()), outcome);
    }

    @Test
    void shotOnShipIsHit() {
        ShotOutcome outcome = board.receiveShot(new Coordinate(1, 0));

        assertEquals(new ShotOutcome(new Coordinate(1, 0), ShotResult.HIT, Optional.empty()), outcome);
        Ship ship = board.ships().getFirst();
        assertTrue(ship.isHitAt(new Coordinate(1, 0)));
        assertFalse(ship.isHitAt(new Coordinate(0, 0)));
        assertFalse(ship.isSunk());
    }

    @Test
    void lastHitOnShipSinksItAndRevealsItsPosition() {
        board.receiveShot(new Coordinate(0, 0));

        ShotOutcome outcome = board.receiveShot(new Coordinate(1, 0));

        assertEquals(new ShotOutcome(new Coordinate(1, 0), ShotResult.SUNK, Optional.of(TWO_SHIP)), outcome);
        assertTrue(board.ships().getFirst().isSunk());
        assertFalse(board.allShipsSunk());
    }

    @Test
    void sinkingTheLastShipIsWin() {
        board.receiveShot(new Coordinate(0, 0));
        board.receiveShot(new Coordinate(1, 0));
        board.receiveShot(new Coordinate(4, 3));

        ShotOutcome outcome = board.receiveShot(new Coordinate(4, 4));

        assertEquals(new ShotOutcome(new Coordinate(4, 4), ShotResult.WIN, Optional.of(SECOND_SHIP)), outcome);
        assertTrue(board.allShipsSunk());
    }

    @Test
    void receivedShotsAreRecorded() {
        board.receiveShot(new Coordinate(2, 2));
        board.receiveShot(new Coordinate(0, 0));

        assertEquals(Set.of(new Coordinate(2, 2), new Coordinate(0, 0)), board.shotsReceived());
    }

    static Stream<Coordinate> alreadyShotCells() {
        return Stream.of(new Coordinate(2, 2), new Coordinate(0, 0));
    }

    @ParameterizedTest
    @MethodSource("alreadyShotCells")
    void shotOnAlreadyShotCellIsRejected(Coordinate target) {
        board.receiveShot(target);

        RuleViolationException exception = assertThrows(RuleViolationException.class,
                () -> board.receiveShot(target));

        assertEquals(Violation.CELL_ALREADY_SHOT, exception.violation());
        assertEquals(Set.of(target), board.shotsReceived());
    }

    static Stream<Coordinate> outOfBoundsTargets() {
        return Stream.of(new Coordinate(-1, 0), new Coordinate(0, -1), new Coordinate(5, 0), new Coordinate(0, 5));
    }

    @ParameterizedTest
    @MethodSource("outOfBoundsTargets")
    void shotOutsideTheBoardIsRejected(Coordinate target) {
        RuleViolationException exception = assertThrows(RuleViolationException.class,
                () -> board.receiveShot(target));

        assertEquals(Violation.SHOT_OUT_OF_BOUNDS, exception.violation());
        assertTrue(board.shotsReceived().isEmpty());
    }

    @ParameterizedTest
    @MethodSource("de.pwe.schiffeversenken.domain.TestFleets#ruleVariants")
    void onlyTheShotSinkingTheLastShipIsWin(RuleSet rules) {
        Board variantBoard = Board.withFleet(rules, rowLayout(rules));
        List<Coordinate> shipCells = rowLayout(rules).stream().flatMap(p -> p.cells().stream()).toList();

        for (Coordinate cell : shipCells.subList(0, shipCells.size() - 1)) {
            ShotResult result = variantBoard.receiveShot(cell).result();
            assertTrue(result == ShotResult.HIT || result == ShotResult.SUNK, "Ergebnis auf " + cell + ": " + result);
        }

        assertEquals(ShotResult.WIN, variantBoard.receiveShot(shipCells.getLast()).result());
        assertTrue(variantBoard.allShipsSunk());
    }
}
