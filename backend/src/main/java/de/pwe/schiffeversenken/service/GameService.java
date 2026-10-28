package de.pwe.schiffeversenken.service;

import de.pwe.schiffeversenken.domain.Coordinate;
import de.pwe.schiffeversenken.domain.ShipPlacement;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.transaction.Transactional;

import java.util.List;
import java.util.UUID;

/**
 * Spielaktionen. Jede Aktion lädt das Spiel, wendet die Domänenlogik an und speichert es (optimistisches Locking).
 * Nicht beteiligte Spieler erhalten {@link ErrorCode#GAME_NOT_FOUND}, damit die Existenz eines Spiels nicht
 * preisgegeben wird.
 */
@ApplicationScoped
public class GameService {

    public PlayerGame get(UUID playerId, UUID gameId) {
        throw new UnsupportedOperationException("Phase 3: noch nicht implementiert");
    }

    /** Eigene Spiele (alle Phasen), zuletzt aktive zuerst. */
    public List<OwnGame> listOwn(UUID playerId) {
        throw new UnsupportedOperationException("Phase 3: noch nicht implementiert");
    }

    @Transactional
    public PlayerGame placeFleet(UUID playerId, UUID gameId, List<ShipPlacement> fleet) {
        throw new UnsupportedOperationException("Phase 3: noch nicht implementiert");
    }

    @Transactional
    public FireResult fire(UUID playerId, UUID gameId, Coordinate target) {
        throw new UnsupportedOperationException("Phase 3: noch nicht implementiert");
    }

    @Transactional
    public PlayerGame resign(UUID playerId, UUID gameId) {
        throw new UnsupportedOperationException("Phase 3: noch nicht implementiert");
    }

    /**
     * Markiert nicht beendete Spiele ohne Aktivität seit {@code game.abandon.inactive-after} als
     * {@code ABANDONED}.
     *
     * @return Anzahl der markierten Spiele
     */
    @Transactional
    public int abandonInactiveGames() {
        throw new UnsupportedOperationException("Phase 3: noch nicht implementiert");
    }
}
