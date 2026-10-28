package de.pwe.schiffeversenken.api.dto;

/** Fehlerantwort: {@code code} ist maschinenlesbar (z. B. {@code NOT_YOUR_TURN}), {@code message} für Menschen. */
public record ErrorResponse(String code, String message) {
}
