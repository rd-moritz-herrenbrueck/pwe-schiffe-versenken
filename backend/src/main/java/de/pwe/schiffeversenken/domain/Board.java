package de.pwe.schiffeversenken.domain;

import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

/**
 * Spielfeld eines Spielers mit platzierter Flotte und empfangenen Schüssen.
 */
public final class Board {

    private final RuleSet rules;
    private final List<Ship> ships;
    private final Set<Coordinate> shotsReceived = new LinkedHashSet<>();

    private Board(RuleSet rules, List<Ship> ships) {
        this.rules = rules;
        this.ships = ships;
    }

    /**
     * Erzeugt ein Board mit validierter Flotte.
     *
     * @throws RuleViolationException wenn die Platzierung nicht zum {@link RuleSet} passt
     */
    public static Board withFleet(RuleSet rules, List<ShipPlacement> placements) {
        Objects.requireNonNull(rules, "rules");
        Objects.requireNonNull(placements, "placements");
        validateFleet(rules, placements);
        return new Board(rules, placements.stream().map(Ship::new).toList());
    }

    /**
     * Wertet einen Schuss auf dieses Board aus.
     *
     * @throws RuleViolationException bei Schüssen außerhalb des Felds oder auf bereits beschossene Felder
     */
    public ShotOutcome receiveShot(Coordinate target) {
        Objects.requireNonNull(target, "target");
        if (!rules.isOnBoard(target)) {
            throw new RuleViolationException(Violation.SHOT_OUT_OF_BOUNDS, "Schuss außerhalb des Felds: " + target);
        }
        if (shotsReceived.contains(target)) {
            throw new RuleViolationException(Violation.CELL_ALREADY_SHOT, "Feld bereits beschossen: " + target);
        }
        shotsReceived.add(target);

        Optional<Ship> hitShip = ships.stream().filter(ship -> ship.occupies(target)).findFirst();
        if (hitShip.isEmpty()) {
            return new ShotOutcome(target, ShotResult.MISS, Optional.empty());
        }
        Ship ship = hitShip.get();
        ship.hit(target);
        if (!ship.isSunk()) {
            return new ShotOutcome(target, ShotResult.HIT, Optional.empty());
        }
        ShotResult result = allShipsSunk() ? ShotResult.WIN : ShotResult.SUNK;
        return new ShotOutcome(target, result, Optional.of(ship.placement()));
    }

    /** Schiffe in der Reihenfolge der Platzierung. */
    public List<Ship> ships() {
        return ships;
    }

    public Set<Coordinate> shotsReceived() {
        return Collections.unmodifiableSet(shotsReceived);
    }

    public boolean allShipsSunk() {
        return ships.stream().allMatch(Ship::isSunk);
    }

    private static void validateFleet(RuleSet rules, List<ShipPlacement> placements) {
        List<Integer> placedLengths = placements.stream().map(ShipPlacement::length).sorted().toList();
        if (!placedLengths.equals(rules.fleet().stream().sorted().toList())) {
            throw new RuleViolationException(Violation.FLEET_MISMATCH,
                    "Schiffslängen " + placedLengths + " entsprechen nicht der Flotte " + rules.fleet());
        }

        Map<Coordinate, Integer> occupiedBy = new HashMap<>();
        for (int i = 0; i < placements.size(); i++) {
            for (Coordinate cell : placements.get(i).cells()) {
                if (!rules.isOnBoard(cell)) {
                    throw new RuleViolationException(Violation.SHIP_OUT_OF_BOUNDS,
                            "Schiff liegt außerhalb des Felds: " + placements.get(i));
                }
                if (occupiedBy.putIfAbsent(cell, i) != null) {
                    throw new RuleViolationException(Violation.SHIPS_OVERLAP, "Schiffe überschneiden sich in " + cell);
                }
            }
        }

        if (!rules.shipsMayTouch()) {
            occupiedBy.forEach((cell, ship) -> {
                for (int dx = -1; dx <= 1; dx++) {
                    for (int dy = -1; dy <= 1; dy++) {
                        Integer neighbour = occupiedBy.get(new Coordinate(cell.x() + dx, cell.y() + dy));
                        if (neighbour != null && !neighbour.equals(ship)) {
                            throw new RuleViolationException(Violation.SHIPS_TOUCHING,
                                    "Schiffe berühren sich bei " + cell);
                        }
                    }
                }
            });
        }
    }
}
