package de.pwe.schiffeversenken.service;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.transaction.Transactional;

import java.util.Optional;

@ApplicationScoped
public class PlayerService {

    /** Maximale Länge eines Nicknames (nach Entfernen von Leerzeichen am Rand). */
    public static final int MAX_NICKNAME_LENGTH = 20;

    /**
     * Legt einen Spieler an. Der Nickname wird getrimmt und muss 1 bis {@value #MAX_NICKNAME_LENGTH} Zeichen lang
     * sein, sonst {@link ErrorCode#INVALID_NICKNAME}.
     */
    @Transactional
    public RegisteredPlayer register(String nickname) {
        throw new UnsupportedOperationException("Phase 3: noch nicht implementiert");
    }

    /** Spieler zum Token, leer bei unbekanntem Token. */
    public Optional<AuthenticatedPlayer> authenticate(String token) {
        throw new UnsupportedOperationException("Phase 3: noch nicht implementiert");
    }
}
