package de.pwe.schiffeversenken.api;

import de.pwe.schiffeversenken.service.AuthenticatedPlayer;
import jakarta.enterprise.context.RequestScoped;
import jakarta.ws.rs.NotAuthorizedException;

/** Spieler der aktuellen Anfrage, gesetzt von {@link AuthenticationFilter}. */
@RequestScoped
public class CurrentPlayer {

    static final String AUTH_SCHEME = "Bearer";

    private AuthenticatedPlayer player;

    void set(AuthenticatedPlayer player) {
        this.player = player;
    }

    /** @throws NotAuthorizedException (401) ohne gültiges Token */
    public AuthenticatedPlayer require() {
        if (player == null) {
            throw new NotAuthorizedException("Gültiges Token erforderlich", AUTH_SCHEME);
        }
        return player;
    }
}
