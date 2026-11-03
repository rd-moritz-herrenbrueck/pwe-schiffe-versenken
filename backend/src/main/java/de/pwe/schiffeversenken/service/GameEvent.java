package de.pwe.schiffeversenken.service;

import de.pwe.schiffeversenken.domain.Coordinate;
import de.pwe.schiffeversenken.domain.GamePhase;
import de.pwe.schiffeversenken.domain.PlayerSide;
import de.pwe.schiffeversenken.domain.ShipPlacement;
import de.pwe.schiffeversenken.domain.ShotOutcome;
import de.pwe.schiffeversenken.domain.ShotResult;

import java.util.UUID;

/**
 * Ereignis eines Spiels. Alle Spieler des Spiels erhalten dieselben Ereignisse, sie enthalten daher nur öffentliche
 * Informationen (nie Schiffspositionen, außer dem versenkten Schiff). Je nach Typ sind nur einzelne Felder gesetzt,
 * die übrigen sind {@code null}.
 */
public record GameEvent(GameEventType type, UUID gameId, PlayerSide side, String nickname, Coordinate target,
                        ShotResult result, ShipPlacement sunkShip, PlayerSide currentTurn, GamePhase phase,
                        PlayerSide winner) {

    public static GameEvent playerJoined(UUID gameId, String nickname) {
        return new GameEvent(GameEventType.PLAYER_JOINED, gameId, null, nickname, null, null, null, null, null, null);
    }

    public static GameEvent fleetReady(UUID gameId, PlayerSide side) {
        return new GameEvent(GameEventType.FLEET_READY, gameId, side, null, null, null, null, null, null, null);
    }

    public static GameEvent gameStarted(UUID gameId, PlayerSide currentTurn) {
        return new GameEvent(GameEventType.GAME_STARTED, gameId, null, null, null, null, null, currentTurn, null,
                null);
    }

    /** Schuss von {@code side} auf das Feld des Gegners. */
    public static GameEvent shot(UUID gameId, PlayerSide side, ShotOutcome outcome) {
        return new GameEvent(GameEventType.SHOT, gameId, side, null, outcome.target(), outcome.result(),
                outcome.sunkShip().orElse(null), null, null, null);
    }

    public static GameEvent turnChanged(UUID gameId, PlayerSide currentTurn) {
        return new GameEvent(GameEventType.TURN_CHANGED, gameId, null, null, null, null, null, currentTurn, null,
                null);
    }

    /** {@code phase} ist {@code FINISHED} oder {@code ABANDONED}, {@code winner} nur bei {@code FINISHED}. */
    public static GameEvent gameFinished(UUID gameId, GamePhase phase, PlayerSide winner) {
        return new GameEvent(GameEventType.GAME_FINISHED, gameId, null, null, null, null, null, null, phase, winner);
    }
}
