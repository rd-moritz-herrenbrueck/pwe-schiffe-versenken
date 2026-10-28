package de.pwe.schiffeversenken.service;

import de.pwe.schiffeversenken.domain.RuleSet;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.transaction.Transactional;

import java.util.List;
import java.util.UUID;

@ApplicationScoped
public class LobbyService {

    /** Erstellt eine öffentliche Lobby. Der Ersteller ist Spieler A. */
    @Transactional
    public PlayerGame create(UUID playerId, RuleSet rules) {
        throw new UnsupportedOperationException("Phase 3: noch nicht implementiert");
    }

    /** Offene öffentliche Lobbys, neueste zuerst. */
    public List<OpenLobby> listOpen() {
        throw new UnsupportedOperationException("Phase 3: noch nicht implementiert");
    }

    /**
     * Tritt als Spieler B bei.
     *
     * @throws ServiceException {@link ErrorCode#GAME_NOT_FOUND}, {@link ErrorCode#CANNOT_JOIN_OWN_GAME}
     * @throws de.pwe.schiffeversenken.domain.RuleViolationException wenn die Lobby nicht mehr offen ist
     */
    @Transactional
    public PlayerGame join(UUID playerId, UUID gameId) {
        throw new UnsupportedOperationException("Phase 3: noch nicht implementiert");
    }
}
