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
import de.pwe.schiffeversenken.persistence.Visibility;
import de.pwe.schiffeversenken.service.ServiceException;
import io.smallrye.common.annotation.Blocking;
import io.smallrye.mutiny.Multi;
import jakarta.inject.Inject;
import jakarta.ws.rs.DELETE;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.Context;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.sse.OutboundSseEvent;
import jakarta.ws.rs.sse.Sse;
import org.jboss.resteasy.reactive.ResponseStatus;

import java.util.List;
import java.util.Map;
import java.util.UUID;

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

    /**
     * Erstellt eine Lobby, öffentlich oder privat mit Einladungscode, oder ein Spiel gegen die KI. Ohne Regeln gelten
     * die Standardregeln.
     */
    @POST
    @ResponseStatus(201)
    public GameView create(CreateLobbyRequest request) {
        UUID playerId = currentPlayer.require().id();
        if (request == null) {
            return toView(lobbyService.create(playerId, RuleSet.standard()));
        }
        Visibility visibility = parseVisibility(request.visibility());
        RuleSet rules = request.rules() == null ? RuleSet.standard() : request.rules().toDomain();
        return toView(lobbyService.create(playerId, rules, visibility, Boolean.TRUE.equals(request.vsAi())));
    }

    /** Tritt per Spiel-ID (öffentliche Lobby) oder per Einladungscode (private Lobby) bei. */
    @POST
    @Path("/join")
    public GameView join(JoinLobbyRequest request) {
        UUID playerId = currentPlayer.require().id();
        if (request != null && request.gameId() != null) {
            return toView(lobbyService.join(playerId, request.gameId()));
        }
        if (request != null && request.inviteCode() != null) {
            return toView(lobbyService.joinByInviteCode(playerId, request.inviteCode()));
        }
        throw new ServiceException(ErrorCode.INVALID_REQUEST, "gameId oder inviteCode fehlt");
    }

    /** Löscht eine eigene Lobby, solange noch kein Gegner beigetreten ist. */
    @DELETE
    @Path("/{id}")
    @ResponseStatus(204)
    public void delete(@PathParam("id") UUID id) {
        lobbyService.delete(currentPlayer.require().id(), id);
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

    private static Visibility parseVisibility(String value) {
        if (value == null) {
            return Visibility.PUBLIC;
        }
        try {
            return Visibility.valueOf(value);
        } catch (IllegalArgumentException e) {
            throw new ServiceException(ErrorCode.INVALID_REQUEST, "visibility muss PUBLIC oder PRIVATE sein");
        }
    }

    private static GameView toView(PlayerGame game) {
        return GameViewMapper.toView(game.id(), game.viewer(), game.opponentNickname(), game.inviteCode(),
                game.game());
    }
}
