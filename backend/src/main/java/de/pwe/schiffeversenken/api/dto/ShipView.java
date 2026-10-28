package de.pwe.schiffeversenken.api.dto;

import de.pwe.schiffeversenken.domain.Orientation;

public record ShipView(int x, int y, int length, Orientation orientation, boolean sunk) {
}
