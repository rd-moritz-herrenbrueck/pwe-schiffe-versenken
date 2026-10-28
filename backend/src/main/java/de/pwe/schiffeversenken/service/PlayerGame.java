package de.pwe.schiffeversenken.service;

import de.pwe.schiffeversenken.domain.Game;
import de.pwe.schiffeversenken.domain.PlayerSide;

import java.util.UUID;

/** Ein Spiel aus Sicht eines beteiligten Spielers. {@code opponentNickname} ist ohne Gegner {@code null}. */
public record PlayerGame(UUID id, PlayerSide viewer, String opponentNickname, Game game) {
}
