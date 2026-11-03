package de.pwe.schiffeversenken.service;

import de.pwe.schiffeversenken.domain.PlayerSide;

import java.time.Instant;
import java.util.UUID;

/** Gespeicherte Chatnachricht mit Seite und Nickname des Absenders. */
public record ChatEntry(UUID id, PlayerSide side, String nickname, String text, Instant createdAt) {
}
