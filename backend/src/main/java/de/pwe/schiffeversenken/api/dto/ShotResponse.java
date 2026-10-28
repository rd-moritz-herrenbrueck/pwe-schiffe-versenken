package de.pwe.schiffeversenken.api.dto;

import de.pwe.schiffeversenken.domain.ShotResult;

/** {@code sunkShip} ist nur bei {@code SUNK} und {@code WIN} gesetzt. */
public record ShotResponse(ShotResult result, ShipPlacementDto sunkShip, GameView game) {
}
