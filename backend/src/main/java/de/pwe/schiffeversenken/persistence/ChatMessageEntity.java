package de.pwe.schiffeversenken.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

/** Chatnachricht eines Spiels. Die Seite des Absenders ergibt sich aus {@code playerId} und dem Spiel. */
@Entity
@Table(name = "chat_message")
public class ChatMessageEntity {

    @Id
    public UUID id;

    @Column(name = "game_id", nullable = false)
    public UUID gameId;

    @Column(name = "player_id", nullable = false)
    public UUID playerId;

    @Column(nullable = false)
    public String text;

    @Column(name = "created_at", nullable = false)
    public Instant createdAt;
}
