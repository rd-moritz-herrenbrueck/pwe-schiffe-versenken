package de.pwe.schiffeversenken.domain;

import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/**
 * Platziertes Schiff mit Trefferzustand.
 */
public final class Ship {

    private final ShipPlacement placement;
    private final List<Coordinate> cells;
    private final Set<Coordinate> hits = new HashSet<>();

    Ship(ShipPlacement placement) {
        this.placement = Objects.requireNonNull(placement, "placement");
        this.cells = placement.cells();
    }

    public ShipPlacement placement() {
        return placement;
    }

    public boolean isHitAt(Coordinate cell) {
        return hits.contains(cell);
    }

    public boolean isSunk() {
        return hits.size() == cells.size();
    }

    boolean occupies(Coordinate cell) {
        return cells.contains(cell);
    }

    void hit(Coordinate cell) {
        if (!occupies(cell)) {
            throw new IllegalArgumentException("Schiff belegt das Feld " + cell + " nicht");
        }
        hits.add(cell);
    }
}
