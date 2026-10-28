package de.pwe.schiffeversenken.api.dto;

import java.util.List;

/**
 * Spielfeld aus Sicht des anfragenden Spielers. Auf dem gegnerischen Feld enthält {@code ships} nur versenkte
 * Schiffe.
 */
public record BoardView(List<ShipView> ships, List<ShotView> shots) {
}
