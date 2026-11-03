package de.pwe.schiffeversenken.persistence;

import io.quarkus.hibernate.orm.panache.PanacheRepositoryBase;
import jakarta.enterprise.context.ApplicationScoped;

import java.util.List;
import java.util.UUID;

@ApplicationScoped
public class ChatMessageRepository implements PanacheRepositoryBase<ChatMessageEntity, UUID> {

    /** Verlauf eines Spiels, älteste Nachricht zuerst. */
    public List<ChatMessageEntity> listByGame(UUID gameId) {
        return list("gameId = ?1 order by createdAt, id", gameId);
    }
}
