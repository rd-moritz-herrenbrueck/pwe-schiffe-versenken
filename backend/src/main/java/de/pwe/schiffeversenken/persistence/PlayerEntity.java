package de.pwe.schiffeversenken.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

/** Anonymer Spieler. Das Token wird nur als Hash gespeichert. */
@Entity
@Table(name = "player")
public class PlayerEntity {

    @Id
    public UUID id;

    @Column(nullable = false)
    public String nickname;

    @Column(name = "token_hash", nullable = false, unique = true)
    public String tokenHash;

    @Column(name = "created_at", nullable = false)
    public Instant createdAt;
}
