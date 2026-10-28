package de.pwe.schiffeversenken.domain;

import java.util.List;
import java.util.Objects;
import java.util.stream.IntStream;

/**
 * Position eines Schiffs: Startkoordinate (oben bzw. links), Länge und Ausrichtung.
 */
public record ShipPlacement(Coordinate start, int length, Orientation orientation) {

    public ShipPlacement {
        Objects.requireNonNull(start, "start");
        Objects.requireNonNull(orientation, "orientation");
    }

    /** Alle belegten Felder, beginnend bei {@link #start()}. */
    public List<Coordinate> cells() {
        return IntStream.range(0, length)
                .mapToObj(i -> orientation == Orientation.HORIZONTAL
                        ? new Coordinate(start.x() + i, start.y())
                        : new Coordinate(start.x(), start.y() + i))
                .toList();
    }
}
