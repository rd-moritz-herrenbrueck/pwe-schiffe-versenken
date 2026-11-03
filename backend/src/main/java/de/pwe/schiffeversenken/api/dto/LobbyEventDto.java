package de.pwe.schiffeversenken.api.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import de.pwe.schiffeversenken.service.LobbyEvent;
import de.pwe.schiffeversenken.service.OpenLobby;

import java.util.UUID;

/** Daten eines SSE-Ereignisses der Lobby-Liste. {@code lobby} ist nur bei {@code LOBBY_OPENED} gesetzt. */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record LobbyEventDto(LobbyEvent.Type type, UUID gameId, LobbySummary lobby) {

    public static LobbyEventDto from(LobbyEvent event) {
        OpenLobby lobby = event.lobby();
        return new LobbyEventDto(event.type(), event.gameId(), lobby == null ? null
                : new LobbySummary(lobby.id(), lobby.creatorNickname(), RuleSetDto.from(lobby.rules()),
                lobby.createdAt()));
    }
}
