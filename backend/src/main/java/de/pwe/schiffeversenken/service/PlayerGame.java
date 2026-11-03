package de.pwe.schiffeversenken.service;

import de.pwe.schiffeversenken.domain.Game;
import de.pwe.schiffeversenken.domain.PlayerSide;

import java.util.UUID;

/**
 * Ein Spiel aus Sicht eines beteiligten Spielers. {@code opponentNickname} ist ohne Gegner {@code null},
 * {@code inviteCode} nur für den Ersteller einer privaten Lobby gesetzt.
 */
public record PlayerGame(UUID id, PlayerSide viewer, String opponentNickname, String inviteCode, Game game) {
}
