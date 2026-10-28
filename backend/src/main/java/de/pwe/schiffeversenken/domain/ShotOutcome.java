package de.pwe.schiffeversenken.domain;

import java.util.Optional;

/**
 * Ergebnis eines Schusses. {@code sunkShip} ist bei {@link ShotResult#SUNK} und {@link ShotResult#WIN} gesetzt.
 */
public record ShotOutcome(Coordinate target, ShotResult result, Optional<ShipPlacement> sunkShip) {
}
