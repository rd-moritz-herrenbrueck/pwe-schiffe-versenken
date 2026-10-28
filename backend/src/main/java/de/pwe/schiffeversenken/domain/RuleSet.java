package de.pwe.schiffeversenken.domain;

import java.util.List;
import java.util.Objects;

/**
 * Spielregeln. Das Feld ist höchstens {@value #MAX_BOARD_SIZE} x {@value #MAX_BOARD_SIZE} groß, die Flotte ist eine
 * frei wählbare Liste gerader Schiffe (nur Länge, mindestens {@value #MIN_SHIP_LENGTH}, Breite 1).
 * Ungültige Regeln werden mit {@link RuleViolationException} abgelehnt: {@link Violation#INVALID_RULE_SET} bei
 * ungültigen Werten, {@link Violation#FLEET_DOES_NOT_FIT}, wenn es keine regelkonforme Platzierung der Flotte gibt
 * (abhängig von {@code shipsMayTouch}).
 */
public record RuleSet(int boardWidth, int boardHeight, List<Integer> fleet, boolean shootAgainOnHit,
                      boolean shipsMayTouch) {

    public static final int MAX_BOARD_SIZE = 25;
    public static final int MIN_SHIP_LENGTH = 2;

    public RuleSet {
        if (!isValidSize(boardWidth) || !isValidSize(boardHeight)) {
            throw new RuleViolationException(Violation.INVALID_RULE_SET,
                    "Feldgröße muss zwischen 1 und " + MAX_BOARD_SIZE + " liegen: " + boardWidth + "x" + boardHeight);
        }
        if (fleet == null || fleet.isEmpty()
                || fleet.stream().anyMatch(length -> length == null || length < MIN_SHIP_LENGTH)) {
            throw new RuleViolationException(Violation.INVALID_RULE_SET,
                    "Flotte muss aus mindestens einem Schiff der Länge >= " + MIN_SHIP_LENGTH + " bestehen: " + fleet);
        }
        fleet = List.copyOf(fleet);
        if (!FleetPlacementSearch.fits(boardWidth, boardHeight, fleet, shipsMayTouch)) {
            throw new RuleViolationException(Violation.FLEET_DOES_NOT_FIT,
                    "Flotte " + fleet + " lässt sich nicht regelkonform auf " + boardWidth + "x" + boardHeight
                            + " platzieren");
        }
    }

    /** Standardregeln: 10x10, je ein Schiff der Länge 5/4/3/2, kein Nochmal-Schießen, keine Berührung. */
    public static RuleSet standard() {
        return new RuleSet(10, 10, List.of(5, 4, 3, 2), false, false);
    }

    /** Liegt das Feld innerhalb des Spielbretts? */
    public boolean isOnBoard(Coordinate cell) {
        Objects.requireNonNull(cell, "cell");
        return cell.x() >= 0 && cell.x() < boardWidth && cell.y() >= 0 && cell.y() < boardHeight;
    }

    private static boolean isValidSize(int size) {
        return size >= 1 && size <= MAX_BOARD_SIZE;
    }
}
