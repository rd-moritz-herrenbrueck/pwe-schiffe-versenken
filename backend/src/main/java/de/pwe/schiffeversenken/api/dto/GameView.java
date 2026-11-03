package de.pwe.schiffeversenken.api.dto;

import de.pwe.schiffeversenken.domain.GamePhase;
import de.pwe.schiffeversenken.domain.PlayerSide;

import java.util.UUID;

/**
 * Spielzustand aus Sicht eines Spielers. Enthält nie die Positionen nicht versenkter gegnerischer Schiffe.
 * {@code opponentNickname} ist {@code null}, solange niemand beigetreten ist, {@code result} bis zum Spielende,
 * {@code inviteCode} nur beim Ersteller einer privaten Lobby.
 */
public record GameView(UUID id, GamePhase phase, PlayerSide you, String opponentNickname, RuleSetDto rules,
                       boolean yourTurn, boolean yourFleetReady, boolean opponentFleetReady, GameResult result,
                       String inviteCode, BoardView ownBoard, BoardView enemyBoard) {
}
