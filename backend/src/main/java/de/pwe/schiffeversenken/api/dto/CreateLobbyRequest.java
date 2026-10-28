package de.pwe.schiffeversenken.api.dto;

/** Ohne {@code rules} gelten die Standardregeln. */
public record CreateLobbyRequest(RuleSetDto rules) {
}
