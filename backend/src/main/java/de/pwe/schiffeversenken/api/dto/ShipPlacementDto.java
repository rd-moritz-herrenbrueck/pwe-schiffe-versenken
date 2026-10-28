package de.pwe.schiffeversenken.api.dto;

import de.pwe.schiffeversenken.domain.Coordinate;
import de.pwe.schiffeversenken.domain.Orientation;
import de.pwe.schiffeversenken.domain.ShipPlacement;

public record ShipPlacementDto(int x, int y, int length, Orientation orientation) {

    public static ShipPlacementDto from(ShipPlacement placement) {
        return new ShipPlacementDto(placement.start().x(), placement.start().y(), placement.length(),
                placement.orientation());
    }

    public ShipPlacement toDomain() {
        return new ShipPlacement(new Coordinate(x, y), length, orientation);
    }
}
