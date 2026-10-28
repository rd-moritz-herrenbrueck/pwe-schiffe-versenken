package de.pwe.schiffeversenken.api;

import de.pwe.schiffeversenken.service.PlayerService;
import jakarta.inject.Inject;
import jakarta.ws.rs.container.ContainerRequestContext;
import jakarta.ws.rs.core.HttpHeaders;
import org.jboss.resteasy.reactive.server.ServerRequestFilter;

/**
 * Liest {@code Authorization: Bearer <token>} und setzt {@link CurrentPlayer}. Ob ein Endpunkt ein Token verlangt,
 * entscheidet der Endpunkt über {@link CurrentPlayer#require()}.
 */
public class AuthenticationFilter {

    private static final String PREFIX = CurrentPlayer.AUTH_SCHEME + " ";

    @Inject
    PlayerService playerService;

    @Inject
    CurrentPlayer currentPlayer;

    @ServerRequestFilter
    public void authenticate(ContainerRequestContext request) {
        String header = request.getHeaderString(HttpHeaders.AUTHORIZATION);
        if (header != null && header.regionMatches(true, 0, PREFIX, 0, PREFIX.length())) {
            playerService.authenticate(header.substring(PREFIX.length()).trim()).ifPresent(currentPlayer::set);
        }
    }
}
