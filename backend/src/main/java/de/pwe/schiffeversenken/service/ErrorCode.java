package de.pwe.schiffeversenken.service;

/** Fachliche Fehler außerhalb der Spielregeln (diese stehen in {@link de.pwe.schiffeversenken.domain.Violation}). */
public enum ErrorCode {
    INVALID_REQUEST,
    INVALID_NICKNAME,
    GAME_NOT_FOUND,
    CANNOT_JOIN_OWN_GAME,
    CONCURRENT_MODIFICATION,
    RATE_LIMITED
}
