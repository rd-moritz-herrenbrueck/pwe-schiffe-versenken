package de.pwe.schiffeversenken.api.dto;

import java.util.UUID;

/** Das Token wird nur bei der Anlage einmalig herausgegeben. */
public record PlayerCreatedResponse(UUID id, String nickname, String token) {
}
