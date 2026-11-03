package de.pwe.schiffeversenken.api.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import de.pwe.schiffeversenken.domain.Coordinate;
import de.pwe.schiffeversenken.domain.GamePhase;
import de.pwe.schiffeversenken.domain.PlayerSide;
import de.pwe.schiffeversenken.domain.ShotResult;
import de.pwe.schiffeversenken.service.GameEvent;
import de.pwe.schiffeversenken.service.GameEventType;

import java.util.UUID;

/**
 * Daten eines SSE-Ereignisses eines Spiels. Nicht gesetzte Felder werden weggelassen. {@code side} ist beim Schuss
 * der Schütze ({@code x}/{@code y}: beschossenes Feld des Gegners), bei {@code FLEET_READY} der Spieler mit fertiger
 * Flotte. {@code sunkShip} ist nur bei {@code SUNK} und {@code WIN} gesetzt, {@code message} nur bei {@code CHAT}.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record GameEventDto(GameEventType type, UUID gameId, PlayerSide side, String nickname, Integer x, Integer y,
                           ShotResult result, ShipPlacementDto sunkShip, PlayerSide currentTurn, GamePhase phase,
                           PlayerSide winner, ChatMessageDto message) {

    public static GameEventDto from(GameEvent event) {
        Coordinate target = event.target();
        return new GameEventDto(event.type(), event.gameId(), event.side(), event.nickname(),
                target == null ? null : target.x(), target == null ? null : target.y(), event.result(),
                event.sunkShip() == null ? null : ShipPlacementDto.from(event.sunkShip()), event.currentTurn(),
                event.phase(), event.winner(),
                event.message() == null ? null : ChatMessageDto.from(event.message()));
    }
}
