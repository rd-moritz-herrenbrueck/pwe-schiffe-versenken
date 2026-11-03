package de.pwe.schiffeversenken.api.dto;

/**
 * Ohne {@code rules} gelten die Standardregeln, ohne {@code visibility} ist die Lobby öffentlich ({@code PUBLIC}
 * oder {@code PRIVATE}). Mit {@code vsAi} spielt der Ersteller gegen die KI.
 */
public record CreateLobbyRequest(RuleSetDto rules, String visibility, Boolean vsAi) {
}
