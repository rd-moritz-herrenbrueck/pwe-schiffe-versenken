package de.pwe.schiffeversenken.api.dto;

/** Fehlende Koordinaten ({@code null}) werden abgelehnt. */
public record ShotRequest(Integer x, Integer y) {
}
