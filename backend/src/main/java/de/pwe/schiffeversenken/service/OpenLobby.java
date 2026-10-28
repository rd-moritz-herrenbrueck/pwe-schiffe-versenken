package de.pwe.schiffeversenken.service;

import de.pwe.schiffeversenken.domain.RuleSet;

import java.time.Instant;
import java.util.UUID;

public record OpenLobby(UUID id, String creatorNickname, RuleSet rules, Instant createdAt) {
}
