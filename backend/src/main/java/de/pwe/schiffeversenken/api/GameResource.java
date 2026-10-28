package de.pwe.schiffeversenken.api;

import de.pwe.schiffeversenken.api.dto.GameResult;
import de.pwe.schiffeversenken.api.dto.GameSummary;
import de.pwe.schiffeversenken.api.dto.GameView;
import de.pwe.schiffeversenken.api.dto.PlaceFleetRequest;
import de.pwe.schiffeversenken.api.dto.ShipPlacementDto;
import de.pwe.schiffeversenken.api.dto.ShotRequest;
import de.pwe.schiffeversenken.api.dto.ShotResponse;
import de.pwe.schiffeversenken.domain.Coordinate;
import de.pwe.schiffeversenken.service.ErrorCode;
import de.pwe.schiffeversenken.service.FireResult;
import de.pwe.schiffeversenken.service.GameService;
import de.pwe.schiffeversenken.service.OwnGame;
import de.pwe.schiffeversenken.service.PlayerGame;
import de.pwe.schiffeversenken.service.ServiceException;
import jakarta.inject.Inject;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.PUT;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;

import java.util.List;
import java.util.Objects;
import java.util.UUID;

@Path("/api/games")
public class GameResource {

    @Inject
    GameService gameService;

    @Inject
    CurrentPlayer currentPlayer;

    /** Eigene Spiele (laufende und beendete), zuletzt aktive zuerst. */
    @GET
    public List<GameSummary> listOwn() {
        return gameService.listOwn(currentPlayer.require().id()).stream().map(GameResource::toSummary).toList();
    }

    @GET
    @Path("/{id}")
    public GameView get(@PathParam("id") UUID id) {
        return toView(gameService.get(currentPlayer.require().id(), id));
    }

    /** Platziert und bestätigt die Flotte in einem Schritt. */
    @PUT
    @Path("/{id}/fleet")
    public GameView placeFleet(@PathParam("id") UUID id, PlaceFleetRequest request) {
        if (request == null || request.ships() == null || request.ships().stream().anyMatch(Objects::isNull)
                || request.ships().stream().anyMatch(ship -> ship.orientation() == null)) {
            throw new ServiceException(ErrorCode.INVALID_REQUEST, "Flotte unvollständig");
        }
        return toView(gameService.placeFleet(currentPlayer.require().id(), id,
                request.ships().stream().map(ShipPlacementDto::toDomain).toList()));
    }

    @POST
    @Path("/{id}/shots")
    public ShotResponse fire(@PathParam("id") UUID id, ShotRequest request) {
        if (request == null || request.x() == null || request.y() == null) {
            throw new ServiceException(ErrorCode.INVALID_REQUEST, "Koordinaten x und y erforderlich");
        }
        FireResult result = gameService.fire(currentPlayer.require().id(), id,
                new Coordinate(request.x(), request.y()));
        return new ShotResponse(result.outcome().result(),
                result.outcome().sunkShip().map(ShipPlacementDto::from).orElse(null), toView(result.game()));
    }

    @POST
    @Path("/{id}/resign")
    public GameView resign(@PathParam("id") UUID id) {
        return toView(gameService.resign(currentPlayer.require().id(), id));
    }

    private static GameView toView(PlayerGame game) {
        return GameViewMapper.toView(game.id(), game.viewer(), game.opponentNickname(), game.game());
    }

    private static GameSummary toSummary(OwnGame game) {
        GameResult result = game.winner() == null ? null
                : game.winner() == game.viewer() ? GameResult.WON : GameResult.LOST;
        return new GameSummary(game.id(), game.phase(), game.opponentNickname(), game.viewer() == game.currentTurn(),
                result, game.updatedAt());
    }
}
