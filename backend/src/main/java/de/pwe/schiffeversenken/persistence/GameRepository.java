package de.pwe.schiffeversenken.persistence;

import io.quarkus.hibernate.orm.panache.PanacheRepositoryBase;
import jakarta.enterprise.context.ApplicationScoped;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@ApplicationScoped
public class GameRepository implements PanacheRepositoryBase<GameEntity, UUID> {

    /** Öffentliche Lobbys in Phase {@code WAITING}, neueste zuerst. */
    public List<GameEntity> listOpenLobbies() {
        throw new UnsupportedOperationException("Phase 3: noch nicht implementiert");
    }

    /** Spiele, an denen der Spieler beteiligt ist, zuletzt aktive zuerst. */
    public List<GameEntity> listByPlayer(UUID playerId) {
        throw new UnsupportedOperationException("Phase 3: noch nicht implementiert");
    }

    /** Nicht beendete Spiele, deren letzte Aktivität vor {@code cutoff} liegt. */
    public List<GameEntity> listUnfinishedInactiveSince(Instant cutoff) {
        throw new UnsupportedOperationException("Phase 3: noch nicht implementiert");
    }
}
