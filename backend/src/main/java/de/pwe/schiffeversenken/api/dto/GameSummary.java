package de.pwe.schiffeversenken.api.dto;

import de.pwe.schiffeversenken.domain.GamePhase;

import java.time.Instant;
import java.util.UUID;

/** Eintrag in der Liste eigener Spiele. */
public record GameSummary(UUID id, GamePhase phase, String opponentNickname, boolean yourTurn, GameResult result,
                          Instant updatedAt) {
}
