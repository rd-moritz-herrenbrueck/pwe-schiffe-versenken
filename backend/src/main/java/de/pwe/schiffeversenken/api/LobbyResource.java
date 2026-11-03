package de.pwe.schiffeversenken.api;

import de.pwe.schiffeversenken.api.dto.CreateLobbyRequest;
import de.pwe.schiffeversenken.api.dto.GameView;
import de.pwe.schiffeversenken.api.dto.JoinLobbyRequest;
import de.pwe.schiffeversenken.api.dto.LobbyEventDto;
import de.pwe.schiffeversenken.api.dto.LobbySummary;
import de.pwe.schiffeversenken.api.dto.RuleSetDto;
import de.pwe.schiffeversenken.domain.RuleSet;
import de.pwe.schiffeversenken.service.ErrorCode;
import de.pwe.schiffeversenken.service.GameEventBus;
import de.pwe.schiffeversenken.service.LobbyService;
import de.pwe.schiffeversenken.service.PlayerGame;
import de.pwe.schiffeversenken.service.ServiceException;
import io.smallrye.common.annotation.Blocking;
import io.smallrye.mutiny.Multi;
import jakarta.inject.Inject;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.Context;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.sse.OutboundSseEvent;
import jakarta.ws.rs.sse.Sse;
import org.jboss.resteasy.reactive.ResponseStatus;

import java.util.List;
import java.util.Map;

@Path("/api/lobbies")
public class LobbyResource {

    @Inject
    LobbyService lobbyService;

    @Inject
    CurrentPlayer currentPlayer;

    @Inject
    GameEventBus eventBus;

    @Inject
    EventStreams eventStreams;

    @GET
    public List<LobbySummary> listOpen() {
        currentPlayer.require();
        return lobbyService.listOpen().stream()
                .map(lobby -> new LobbySummary(lobby.id(), lobby.creatorNickname(), RuleSetDto.from(lobby.rules()),
                        lobby.createdAt()))
                .toList();
    }

    /** Erstellt eine öffentliche Lobby. Ohne Regeln gelten die Standardregeln. */
    @POST
    @ResponseStatus(201)
    public GameView create(CreateLobbyRequest request) {
        RuleSet rules = request == null || request.rules() == null ? RuleSet.standard() : request.rules().toDomain();
        return toView(lobbyService.create(currentPlayer.require().id(), rules));
    }

    @POST
    @Path("/join")
    public GameView join(JoinLobbyRequest request) {
        if (request == null || request.gameId() == null) {
            throw new ServiceException(ErrorCode.INVALID_REQUEST, "gameId fehlt");
        }
        return toView(lobbyService.join(currentPlayer.require().id(), request.gameId()));
    }

    /** Änderungen der Liste offener Lobbys als Server-Sent Events, siehe {@link EventStreams}. */
    @GET
    @Path("/events")
    @Produces(MediaType.SERVER_SENT_EVENTS)
    @Blocking
    public Multi<OutboundSseEvent> events(@Context Sse sse) {
        currentPlayer.require();
        return eventStreams.stream(sse, new EventStreams.Named("CONNECTED", Map.of("type", "CONNECTED")),
                eventBus.subscribeToLobbies().map(event -> new EventStreams.Named(event.type().name(),
                        LobbyEventDto.from(event))));
    }

    private static GameView toView(PlayerGame game) {
        return GameViewMapper.toView(game.id(), game.viewer(), game.opponentNickname(), game.game());
    }
}
