package de.pwe.schiffeversenken.domain;

import org.junit.jupiter.api.Test;

import java.util.List;

import static de.pwe.schiffeversenken.domain.TestFleets.horizontal;
import static de.pwe.schiffeversenken.domain.TestFleets.vertical;
import static org.junit.jupiter.api.Assertions.assertEquals;

class ShipPlacementTest {

    @Test
    void horizontalShipOccupiesCellsToTheRight() {
        assertEquals(
                List.of(new Coordinate(2, 3), new Coordinate(3, 3), new Coordinate(4, 3)),
                horizontal(2, 3, 3).cells());
    }

    @Test
    void verticalShipOccupiesCellsDownwards() {
        assertEquals(
                List.of(new Coordinate(2, 3), new Coordinate(2, 4)),
                vertical(2, 3, 2).cells());
    }
}
