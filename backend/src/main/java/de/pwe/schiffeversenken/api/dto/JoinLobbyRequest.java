package de.pwe.schiffeversenken.api.dto;

import java.util.UUID;

/** Beitritt per Spiel-ID (öffentliche Lobbys) oder Einladungscode (private Lobbys). */
public record JoinLobbyRequest(UUID gameId, String inviteCode) {
}
