package de.pwe.schiffeversenken.domain;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;

class RuleSetTest {

    @Test
    void standardRulesUseTenByTenBoardWithOneShipOfEachLengthFromFiveToTwo() {
        RuleSet rules = RuleSet.standard();

        assertEquals(10, rules.boardWidth());
        assertEquals(10, rules.boardHeight());
        assertEquals(List.of(5, 4, 3, 2), rules.fleet());
        assertFalse(rules.shootAgainOnHit());
        assertFalse(rules.shipsMayTouch());
    }

    @Test
    void fleetIsNotAffectedByLaterChangesToTheInputList() {
        List<Integer> fleet = new ArrayList<>(List.of(3, 2));
        RuleSet rules = new RuleSet(5, 5, fleet, false, false);

        fleet.add(4);

        assertEquals(List.of(3, 2), rules.fleet());
    }

    static Stream<Arguments> invalidRuleSets() {
        return Stream.of(
                Arguments.of("Breite 0", 0, 10, List.of(2)),
                Arguments.of("Höhe 0", 10, 0, List.of(2)),
                Arguments.of("negative Breite", -1, 10, List.of(2)),
                Arguments.of("Breite über Maximum", 26, 10, List.of(2)),
                Arguments.of("Höhe über Maximum", 10, 26, List.of(2)),
                Arguments.of("leere Flotte", 10, 10, List.of()),
                Arguments.of("Schiffslänge 0", 10, 10, List.of(3, 0)),
                Arguments.of("Schiffslänge 1 (Minimum ist 2)", 10, 10, List.of(3, 1)),
                Arguments.of("negative Schiffslänge", 10, 10, List.of(-2)));
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("invalidRuleSets")
    void invalidRuleSetIsRejected(String description, int width, int height, List<Integer> fleet) {
        RuleViolationException exception = assertThrows(RuleViolationException.class,
                () -> new RuleSet(width, height, fleet, false, false));

        assertEquals(Violation.INVALID_RULE_SET, exception.violation());
    }

    static Stream<Arguments> fleetsThatFit() {
        return Stream.of(
                Arguments.of("Standard ohne Berührung", 10, 10, List.of(5, 4, 3, 2), false),
                Arguments.of("Standard mit Berührung", 10, 10, List.of(5, 4, 3, 2), true),
                Arguments.of("individuelle Längen", 10, 10, List.of(7, 6, 6, 2, 2), false),
                Arguments.of("Schiff so lang wie das Feld", 25, 3, List.of(25), false),
                Arguments.of("knapp ohne Berührung: jede zweite Zeile", 5, 5, List.of(5, 5, 5), false),
                Arguments.of("knapp ohne Berührung: vier Zweier auf 4x4", 4, 4, List.of(2, 2, 2, 2), false),
                Arguments.of("Feld komplett gefüllt mit Berührung: zwei Zweier auf 2x2", 2, 2, List.of(2, 2), true),
                Arguments.of("Feld komplett gefüllt mit Berührung", 5, 5, List.of(5, 5, 5, 5, 5), true),
                Arguments.of("Maximalfeld knapp ohne Berührung", 25, 25, Collections.nCopies(13, 25), false),
                Arguments.of("viele kleine Schiffe dicht ohne Berührung", 12, 12, Collections.nCopies(27, 2), false));
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("fleetsThatFit")
    @Timeout(2)
    void fleetThatFitsOnTheBoardIsAccepted(String description, int width, int height, List<Integer> fleet,
                                           boolean shipsMayTouch) {
        assertDoesNotThrow(() -> new RuleSet(width, height, fleet, false, shipsMayTouch));
    }

    static Stream<Arguments> fleetsThatDoNotFit() {
        return Stream.of(
                Arguments.of("Schiff länger als das Feld", 4, 3, List.of(5), true),
                Arguments.of("mehr Schiffsfelder als Feldfläche", 2, 2, List.of(2, 2, 2), true),
                Arguments.of("Abstand fehlt: vier Fünfer auf 5x5", 5, 5, List.of(5, 5, 5, 5), false),
                Arguments.of("Abstand fehlt: fünf Zweier auf 4x4", 4, 4, List.of(2, 2, 2, 2, 2), false),
                Arguments.of("Abstand fehlt: zwei Zweier auf 2x2", 2, 2, List.of(2, 2), false),
                Arguments.of("Fläche reicht rechnerisch, Platzierung unmöglich", 4, 4, List.of(3, 3, 3), false),
                Arguments.of("Maximalfeld ohne Berührung überfüllt", 25, 25, Collections.nCopies(14, 25), false));
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("fleetsThatDoNotFit")
    @Timeout(2)
    void fleetThatCannotBePlacedIsRejected(String description, int width, int height, List<Integer> fleet,
                                           boolean shipsMayTouch) {
        RuleViolationException exception = assertThrows(RuleViolationException.class,
                () -> new RuleSet(width, height, fleet, false, shipsMayTouch));

        assertEquals(Violation.FLEET_DOES_NOT_FIT, exception.violation());
    }

    @Test
    @Timeout(2)
    void extremelyDenseFleetIsDecidedInBoundedTime() {
        try {
            new RuleSet(24, 24, Collections.nCopies(74, 3), false, false);
        } catch (RuleViolationException exception) {
            assertEquals(Violation.FLEET_DOES_NOT_FIT, exception.violation());
        }
    }
}
