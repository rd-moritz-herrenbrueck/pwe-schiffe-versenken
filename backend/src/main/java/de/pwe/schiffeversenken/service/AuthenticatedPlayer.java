package de.pwe.schiffeversenken.service;

import java.util.UUID;

public record AuthenticatedPlayer(UUID id, String nickname) {
}
