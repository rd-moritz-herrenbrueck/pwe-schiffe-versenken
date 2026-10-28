package de.pwe.schiffeversenken.api;

import de.pwe.schiffeversenken.api.dto.CreatePlayerRequest;
import de.pwe.schiffeversenken.api.dto.PlayerCreatedResponse;
import de.pwe.schiffeversenken.service.ErrorCode;
import de.pwe.schiffeversenken.service.PlayerService;
import de.pwe.schiffeversenken.service.RegisteredPlayer;
import de.pwe.schiffeversenken.service.ServiceException;
import jakarta.inject.Inject;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import org.jboss.resteasy.reactive.ResponseStatus;

@Path("/api/players")
public class PlayerResource {

    @Inject
    PlayerService playerService;

    /** Legt einen Spieler an. Einziger Endpunkt ohne Token. */
    @POST
    @ResponseStatus(201)
    public PlayerCreatedResponse create(CreatePlayerRequest request) {
        if (request == null) {
            throw new ServiceException(ErrorCode.INVALID_REQUEST, "Nickname fehlt");
        }
        RegisteredPlayer player = playerService.register(request.nickname());
        return new PlayerCreatedResponse(player.id(), player.nickname(), player.token());
    }
}
