package de.pwe.schiffeversenken.api.dto;

import java.time.Instant;
import java.util.UUID;

/** Offene Lobby in der Lobby-Liste. */
public record LobbySummary(UUID id, String creatorNickname, RuleSetDto rules, Instant createdAt) {
}
