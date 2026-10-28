package de.pwe.schiffeversenken.domain;

/**
 * Feld auf dem Spielbrett, 0-basiert. {@code x} ist die Spalte, {@code y} die Zeile.
 */
public record Coordinate(int x, int y) {
}
