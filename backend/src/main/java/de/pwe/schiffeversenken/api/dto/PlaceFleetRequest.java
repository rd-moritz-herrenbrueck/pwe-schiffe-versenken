package de.pwe.schiffeversenken.api.dto;

import java.util.List;

public record PlaceFleetRequest(List<ShipPlacementDto> ships) {
}
