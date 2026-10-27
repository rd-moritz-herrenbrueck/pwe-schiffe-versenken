package de.pwe.schiffeversenken.domain;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

/**
 * Feste, regelkonforme Testaufstellungen.
 */
final class TestFleets {

    private TestFleets() {
    }

    /** Standardregeln mit Nochmal-Schießen nach Treffer. */
    static RuleSet standardWithShootAgain() {
        return new RuleSet(10, 10, List.of(5, 4, 3, 2), true, false);
    }

    /** Regelvarianten inkl. Standard, für die {@link #rowLayout(RuleSet)} gültig ist. */
    static Stream<RuleSet> ruleVariants() {
        return Stream.of(
                RuleSet.standard(),
                standardWithShootAgain(),
                new RuleSet(8, 8, List.of(4, 3, 2, 2), false, false),
                new RuleSet(12, 6, List.of(5, 4, 3), true, false),
                new RuleSet(15, 15, List.of(6, 5, 4, 4, 3, 3, 2, 2), false, true),
                new RuleSet(RuleSet.MAX_BOARD_SIZE, RuleSet.MAX_BOARD_SIZE, List.of(25, 12, 7, 2, 2), false, false));
    }

    /**
     * Schiff i liegt horizontal ab (0, 2*i). Zwischen den Schiffen bleibt je eine leere Zeile.
     */
    static List<ShipPlacement> rowLayout(RuleSet rules) {
        List<ShipPlacement> placements = new ArrayList<>();
        for (int i = 0; i < rules.fleet().size(); i++) {
            placements.add(horizontal(0, 2 * i, rules.fleet().get(i)));
        }
        return placements;
    }

    static ShipPlacement horizontal(int x, int y, int length) {
        return new ShipPlacement(new Coordinate(x, y), length, Orientation.HORIZONTAL);
    }

    static ShipPlacement vertical(int x, int y, int length) {
        return new ShipPlacement(new Coordinate(x, y), length, Orientation.VERTICAL);
    }
}
