package de.pwe.schiffeversenken.domain;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

import static de.pwe.schiffeversenken.domain.TestFleets.horizontal;
import static de.pwe.schiffeversenken.domain.TestFleets.rowLayout;
import static de.pwe.schiffeversenken.domain.TestFleets.vertical;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BoardPlacementTest {

    private static final RuleSet TWO_SHIPS_NO_TOUCH = new RuleSet(5, 5, List.of(2, 2), false, false);
    private static final RuleSet TWO_SHIPS_MAY_TOUCH = new RuleSet(5, 5, List.of(2, 2), false, true);

    @ParameterizedTest
    @MethodSource("de.pwe.schiffeversenken.domain.TestFleets#ruleVariants")
    void validFleetIsPlaced(RuleSet rules) {
        List<ShipPlacement> placements = rowLayout(rules);

        Board board = Board.withFleet(rules, placements);

        assertEquals(placements, board.ships().stream().map(Ship::placement).toList());
        assertTrue(board.shotsReceived().isEmpty());
        assertFalse(board.allShipsSunk());
    }

    @Test
    void fleetOrderDoesNotMatter() {
        RuleSet rules = RuleSet.standard();
        List<ShipPlacement> placements = new ArrayList<>(rowLayout(rules));
        placements.addFirst(placements.removeLast());

        assertDoesNotThrow(() -> Board.withFleet(rules, placements));
    }

    static Stream<Arguments> outOfBoundsPlacements() {
        return Stream.of(
                Arguments.of("ragt rechts hinaus", horizontal(6, 0, 5)),
                Arguments.of("beginnt links außerhalb", horizontal(-1, 0, 5)),
                Arguments.of("ragt unten hinaus", vertical(9, 6, 5)),
                Arguments.of("beginnt oben außerhalb", vertical(9, -1, 5)),
                Arguments.of("komplett außerhalb", horizontal(20, 20, 5)));
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("outOfBoundsPlacements")
    void shipOutsideTheBoardIsRejected(String description, ShipPlacement fiveShip) {
        RuleSet rules = RuleSet.standard();
        List<ShipPlacement> placements = new ArrayList<>(rowLayout(rules));
        placements.set(0, fiveShip);

        assertViolation(Violation.SHIP_OUT_OF_BOUNDS, rules, placements);
    }

    @ParameterizedTest
    @ValueSource(booleans = {true, false})
    void overlappingShipsAreRejectedRegardlessOfTouchRule(boolean shipsMayTouch) {
        RuleSet rules = new RuleSet(5, 5, List.of(2, 2), false, shipsMayTouch);

        assertViolation(Violation.SHIPS_OVERLAP, rules, List.of(horizontal(0, 0, 2), vertical(1, 0, 2)));
    }

    static Stream<Arguments> touchingPlacements() {
        return Stream.of(
                Arguments.of("parallel nebeneinander", List.of(horizontal(0, 0, 2), horizontal(0, 1, 2))),
                Arguments.of("Ende an Ende", List.of(horizontal(0, 0, 2), horizontal(2, 0, 2))),
                Arguments.of("diagonal", List.of(horizontal(0, 0, 2), vertical(2, 1, 2))));
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("touchingPlacements")
    void touchingShipsAreRejectedWhenNotAllowed(String description, List<ShipPlacement> placements) {
        assertViolation(Violation.SHIPS_TOUCHING, TWO_SHIPS_NO_TOUCH, placements);
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("touchingPlacements")
    void touchingShipsAreAcceptedWhenAllowed(String description, List<ShipPlacement> placements) {
        assertDoesNotThrow(() -> Board.withFleet(TWO_SHIPS_MAY_TOUCH, placements));
    }

    @Test
    void shipsSeparatedByOneCellDoNotTouch() {
        assertDoesNotThrow(() -> Board.withFleet(TWO_SHIPS_NO_TOUCH,
                List.of(horizontal(0, 0, 2), horizontal(0, 2, 2))));
    }

    static Stream<Arguments> fleetMismatches() {
        List<ShipPlacement> valid = rowLayout(RuleSet.standard());
        List<ShipPlacement> missingShip = valid.subList(0, 3);
        List<ShipPlacement> extraShip = new ArrayList<>(valid);
        extraShip.add(horizontal(5, 9, 2));
        List<ShipPlacement> wrongLength = new ArrayList<>(valid);
        wrongLength.set(3, horizontal(0, 6, 3));
        return Stream.of(
                Arguments.of("keine Schiffe", List.of()),
                Arguments.of("Schiff fehlt", missingShip),
                Arguments.of("Schiff zu viel", extraShip),
                Arguments.of("falsche Länge", wrongLength));
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("fleetMismatches")
    void fleetNotMatchingTheRuleSetIsRejected(String description, List<ShipPlacement> placements) {
        assertViolation(Violation.FLEET_MISMATCH, RuleSet.standard(), placements);
    }

    private static void assertViolation(Violation expected, RuleSet rules, List<ShipPlacement> placements) {
        RuleViolationException exception = assertThrows(RuleViolationException.class,
                () -> Board.withFleet(rules, placements));
        assertEquals(expected, exception.violation());
    }
}
