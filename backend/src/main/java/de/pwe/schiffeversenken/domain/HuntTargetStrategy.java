package de.pwe.schiffeversenken.domain;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.random.RandomGenerator;

/**
 * Hunt/Target: Ohne offenen Treffer wird zufällig gesucht (Hunt), nach einem Treffer werden die Nachbarfelder
 * verfolgt, bis das Schiff versenkt ist (Target). Zwei Treffer in einer Reihe verlängern die Linie. Ohne
 * {@code shipsMayTouch} werden Felder rund um versenkte Schiffe nicht beschossen. Der Zufall ist injizierbar, damit
 * Tests deterministisch sind.
 */
public final class HuntTargetStrategy implements AiStrategy {

    private static final int[][] DIRECTIONS = {{1, 0}, {-1, 0}, {0, 1}, {0, -1}};

    private final RandomGenerator random;

    public HuntTargetStrategy(RandomGenerator random) {
        this.random = random;
    }

    @Override
    public List<ShipPlacement> placeFleet(RuleSet rules) {
        List<Integer> lengths = rules.fleet().stream().sorted(Comparator.reverseOrder()).toList();
        List<ShipPlacement> placed = new ArrayList<>();
        if (!place(rules, lengths, placed)) {
            throw new IllegalStateException("Keine Platzierung gefunden für " + rules);
        }
        return placed;
    }

    /** Tiefensuche über zufällig sortierte Positionen, längste Schiffe zuerst. Findet jede vorhandene Lösung. */
    private boolean place(RuleSet rules, List<Integer> lengths, List<ShipPlacement> placed) {
        if (placed.size() == lengths.size()) {
            return true;
        }
        int length = lengths.get(placed.size());
        List<ShipPlacement> candidates = new ArrayList<>();
        for (int x = 0; x < rules.boardWidth(); x++) {
            for (int y = 0; y < rules.boardHeight(); y++) {
                for (Orientation orientation : Orientation.values()) {
                    ShipPlacement candidate = new ShipPlacement(new Coordinate(x, y), length, orientation);
                    if (isOnBoard(rules, candidate) && isCompatible(rules, candidate, placed)) {
                        candidates.add(candidate);
                    }
                }
            }
        }
        shuffle(candidates);
        for (ShipPlacement candidate : candidates) {
            placed.add(candidate);
            if (place(rules, lengths, placed)) {
                return true;
            }
            placed.removeLast();
        }
        return false;
    }

    private static boolean isOnBoard(RuleSet rules, ShipPlacement ship) {
        return ship.cells().stream().allMatch(rules::isOnBoard);
    }

    private static boolean isCompatible(RuleSet rules, ShipPlacement ship, List<ShipPlacement> placed) {
        for (ShipPlacement other : placed) {
            for (Coordinate a : ship.cells()) {
                for (Coordinate b : other.cells()) {
                    int dx = Math.abs(a.x() - b.x());
                    int dy = Math.abs(a.y() - b.y());
                    boolean touching = dx <= 1 && dy <= 1;
                    if (touching && (!rules.shipsMayTouch() || (dx == 0 && dy == 0))) {
                        return false;
                    }
                }
            }
        }
        return true;
    }

    private <T> void shuffle(List<T> list) {
        for (int i = list.size() - 1; i > 0; i--) {
            int j = random.nextInt(i + 1);
            T swap = list.get(i);
            list.set(i, list.get(j));
            list.set(j, swap);
        }
    }

    @Override
    public Coordinate nextShot(RuleSet rules, List<ShotOutcome> shotsSoFar) {
        Set<Coordinate> shot = new HashSet<>();
        Set<Coordinate> sunkCells = new HashSet<>();
        Set<Coordinate> aroundSunk = new HashSet<>();
        for (ShotOutcome outcome : shotsSoFar) {
            shot.add(outcome.target());
            outcome.sunkShip().ifPresent(ship -> ship.cells().forEach(cell -> {
                sunkCells.add(cell);
                for (int dx = -1; dx <= 1; dx++) {
                    for (int dy = -1; dy <= 1; dy++) {
                        aroundSunk.add(new Coordinate(cell.x() + dx, cell.y() + dy));
                    }
                }
            }));
        }
        Set<Coordinate> openHits = new HashSet<>();
        for (ShotOutcome outcome : shotsSoFar) {
            if (outcome.result() == ShotResult.HIT && !sunkCells.contains(outcome.target())) {
                openHits.add(outcome.target());
            }
        }

        List<Coordinate> lineEnds = new ArrayList<>();
        List<Coordinate> neighbours = new ArrayList<>();
        for (Coordinate hit : openHits) {
            for (int[] direction : DIRECTIONS) {
                Coordinate cell = new Coordinate(hit.x() + direction[0], hit.y() + direction[1]);
                boolean continuesLine = openHits.contains(cell);
                while (openHits.contains(cell)) {
                    cell = new Coordinate(cell.x() + direction[0], cell.y() + direction[1]);
                }
                if (rules.isOnBoard(cell) && !shot.contains(cell)) {
                    (continuesLine ? lineEnds : neighbours).add(cell);
                }
            }
        }
        if (!lineEnds.isEmpty()) {
            return pick(lineEnds);
        }
        if (!neighbours.isEmpty()) {
            return pick(neighbours);
        }
        return pick(huntTargets(rules, shot, aroundSunk));
    }

    private List<Coordinate> huntTargets(RuleSet rules, Set<Coordinate> shot, Set<Coordinate> aroundSunk) {
        List<Coordinate> preferred = new ArrayList<>();
        List<Coordinate> remaining = new ArrayList<>();
        for (int x = 0; x < rules.boardWidth(); x++) {
            for (int y = 0; y < rules.boardHeight(); y++) {
                Coordinate cell = new Coordinate(x, y);
                if (shot.contains(cell)) {
                    continue;
                }
                remaining.add(cell);
                if (rules.shipsMayTouch() || !aroundSunk.contains(cell)) {
                    preferred.add(cell);
                }
            }
        }
        if (remaining.isEmpty()) {
            throw new IllegalStateException("Auf alle Felder wurde bereits geschossen");
        }
        return preferred.isEmpty() ? remaining : preferred;
    }

    private Coordinate pick(List<Coordinate> cells) {
        return cells.get(random.nextInt(cells.size()));
    }
}
