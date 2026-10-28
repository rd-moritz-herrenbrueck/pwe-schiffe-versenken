package de.pwe.schiffeversenken.persistence;

import de.pwe.schiffeversenken.domain.GamePhase;
import de.pwe.schiffeversenken.domain.PlayerSide;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;
import java.util.UUID;

/**
 * Gespeichertes Spiel. Regeln und Spielfelder liegen als JSONB in {@code rules} bzw. {@code state} (siehe
 * {@link GameStateMapper}); Phase, Zug und Sieger zusätzlich als Spalten für Abfragen.
 */
@Entity
@Table(name = "game")
public class GameEntity {

    @Id
    public UUID id;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    public GamePhase status;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    public Visibility visibility;

    @Column(name = "invite_code")
    public String inviteCode;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(nullable = false)
    public String rules;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(nullable = false)
    public String state;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "player_a", nullable = false)
    public PlayerEntity playerA;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "player_b")
    public PlayerEntity playerB;

    @Column(name = "ai_opponent", nullable = false)
    public boolean aiOpponent;

    @Enumerated(EnumType.STRING)
    @Column(name = "current_turn")
    public PlayerSide currentTurn;

    @Enumerated(EnumType.STRING)
    public PlayerSide winner;

    @Version
    public long version;

    @Column(name = "created_at", nullable = false)
    public Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    public Instant updatedAt;
}
