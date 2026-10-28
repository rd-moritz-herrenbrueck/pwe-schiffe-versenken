package de.pwe.schiffeversenken.domain;

import java.util.List;
import java.util.Objects;

/**
 * Speicherbarer Zustand eines {@link Board}: Flotte in Platzierungsreihenfolge und empfangene Schüsse in
 * Schussreihenfolge. Der Trefferzustand der Schiffe wird beim Wiederherstellen aus den Schüssen abgeleitet.
 */
public record BoardSnapshot(List<ShipPlacement> ships, List<Coordinate> shotsReceived) {

    public BoardSnapshot {
        ships = List.copyOf(Objects.requireNonNull(ships, "ships"));
        shotsReceived = List.copyOf(Objects.requireNonNull(shotsReceived, "shotsReceived"));
    }
}
