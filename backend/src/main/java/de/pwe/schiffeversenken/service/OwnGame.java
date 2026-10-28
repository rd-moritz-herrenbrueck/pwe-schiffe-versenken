package de.pwe.schiffeversenken.service;

import de.pwe.schiffeversenken.domain.GamePhase;
import de.pwe.schiffeversenken.domain.PlayerSide;

import java.time.Instant;
import java.util.UUID;

/** Eintrag der eigenen Spieleliste, ohne Spielfelder. {@code currentTurn} und {@code winner} sind optional. */
public record OwnGame(UUID id, GamePhase phase, PlayerSide viewer, String opponentNickname, PlayerSide currentTurn,
                      PlayerSide winner, Instant updatedAt) {
}
