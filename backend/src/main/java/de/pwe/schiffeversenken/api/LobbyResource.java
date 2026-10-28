package de.pwe.schiffeversenken.api;

import de.pwe.schiffeversenken.api.dto.CreateLobbyRequest;
import de.pwe.schiffeversenken.api.dto.GameView;
import de.pwe.schiffeversenken.api.dto.JoinLobbyRequest;
import de.pwe.schiffeversenken.api.dto.LobbySummary;
import de.pwe.schiffeversenken.api.dto.RuleSetDto;
import de.pwe.schiffeversenken.domain.RuleSet;
import de.pwe.schiffeversenken.service.ErrorCode;
import de.pwe.schiffeversenken.service.LobbyService;
import de.pwe.schiffeversenken.service.PlayerGame;
import de.pwe.schiffeversenken.service.ServiceException;
import jakarta.inject.Inject;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import org.jboss.resteasy.reactive.ResponseStatus;

import java.util.List;

@Path("/api/lobbies")
public class LobbyResource {

    @Inject
    LobbyService lobbyService;

    @Inject
    CurrentPlayer currentPlayer;

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

    private static GameView toView(PlayerGame game) {
        return GameViewMapper.toView(game.id(), game.viewer(), game.opponentNickname(), game.game());
    }
}
