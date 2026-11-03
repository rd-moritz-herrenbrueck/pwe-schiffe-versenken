package de.pwe.schiffeversenken.api;

import de.pwe.schiffeversenken.service.PlayerService;
import jakarta.inject.Inject;
import jakarta.ws.rs.container.ContainerRequestContext;
import jakarta.ws.rs.core.HttpHeaders;
import org.jboss.resteasy.reactive.server.ServerRequestFilter;

import java.util.regex.Pattern;

/**
 * Liest das Token und setzt {@link CurrentPlayer}. Ob ein Endpunkt ein Token verlangt, entscheidet der Endpunkt über
 * {@link CurrentPlayer#require()}.
 *
 * <p>Üblich ist der {@code Authorization}-Header. Der Browser-{@code EventSource} kann keine Header senden, daher
 * gilt für die SSE-Endpunkte zusätzlich der Query-Parameter {@code access_token}, für alle anderen nicht.
 */
public class AuthenticationFilter {

    private static final String PREFIX = CurrentPlayer.AUTH_SCHEME + " ";
    private static final String TOKEN_PARAMETER = "access_token";
    private static final Pattern EVENT_STREAM_PATH = Pattern.compile("^/?api/(lobbies/events|games/[^/]+/events)$");

    @Inject
    PlayerService playerService;

    @Inject
    CurrentPlayer currentPlayer;

    @ServerRequestFilter
    public void authenticate(ContainerRequestContext request) {
        String token = bearerToken(request);
        if (token == null && EVENT_STREAM_PATH.matcher(request.getUriInfo().getPath()).matches()) {
            token = request.getUriInfo().getQueryParameters().getFirst(TOKEN_PARAMETER);
        }
        if (token != null) {
            playerService.authenticate(token).ifPresent(currentPlayer::set);
        }
    }

    private static String bearerToken(ContainerRequestContext request) {
        String header = request.getHeaderString(HttpHeaders.AUTHORIZATION);
        if (header != null && header.regionMatches(true, 0, PREFIX, 0, PREFIX.length())) {
            return header.substring(PREFIX.length()).trim();
        }
        return null;
    }
}
