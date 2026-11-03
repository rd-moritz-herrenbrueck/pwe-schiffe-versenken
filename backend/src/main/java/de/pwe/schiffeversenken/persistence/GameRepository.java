package de.pwe.schiffeversenken.persistence;

import de.pwe.schiffeversenken.domain.GamePhase;
import io.quarkus.hibernate.orm.panache.PanacheRepositoryBase;
import jakarta.enterprise.context.ApplicationScoped;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@ApplicationScoped
public class GameRepository implements PanacheRepositoryBase<GameEntity, UUID> {

    /** Öffentliche Lobbys in Phase {@code WAITING}, neueste zuerst. */
    public List<GameEntity> listOpenLobbies() {
        return find("from GameEntity g join fetch g.playerA where g.status = ?1 and g.visibility = ?2"
                        + " order by g.createdAt desc, g.id",
                GamePhase.WAITING, Visibility.PUBLIC).list();
    }

    /** Spiele, an denen der Spieler beteiligt ist, zuletzt aktive zuerst. */
    public List<GameEntity> listByPlayer(UUID playerId) {
        return find("from GameEntity g join fetch g.playerA left join fetch g.playerB"
                        + " where g.playerA.id = ?1 or g.playerB.id = ?1 order by g.updatedAt desc, g.id",
                playerId).list();
    }

    /** Nicht beendete Spiele, deren letzte Aktivität vor {@code cutoff} liegt. */
    public List<GameEntity> listUnfinishedInactiveSince(Instant cutoff) {
        return list("status in ?1 and updatedAt < ?2",
                List.of(GamePhase.WAITING, GamePhase.PLACEMENT, GamePhase.RUNNING), cutoff);
    }
}
