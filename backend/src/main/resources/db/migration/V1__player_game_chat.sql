-- Anonyme Spieler: Das Token wird nur als SHA-256-Hash gespeichert.
CREATE TABLE player (
    id         UUID PRIMARY KEY,
    nickname   VARCHAR(20)  NOT NULL,
    token_hash VARCHAR(64)  NOT NULL UNIQUE,
    created_at TIMESTAMPTZ  NOT NULL
);

-- Spiel: Regeln und Spielfelder als JSONB, Phase/Zug/Sieger zusätzlich als Spalten für Abfragen.
CREATE TABLE game (
    id           UUID PRIMARY KEY,
    status       VARCHAR(16)  NOT NULL
        CHECK (status IN ('WAITING', 'PLACEMENT', 'RUNNING', 'FINISHED', 'ABANDONED')),
    visibility   VARCHAR(16)  NOT NULL
        CHECK (visibility IN ('PUBLIC', 'PRIVATE')),
    invite_code  VARCHAR(16),
    rules        JSONB        NOT NULL,
    state        JSONB        NOT NULL,
    player_a     UUID         NOT NULL REFERENCES player (id),
    player_b     UUID         REFERENCES player (id),
    ai_opponent  BOOLEAN      NOT NULL DEFAULT FALSE,
    current_turn VARCHAR(1)   CHECK (current_turn IN ('A', 'B')),
    winner       VARCHAR(1)   CHECK (winner IN ('A', 'B')),
    version      BIGINT       NOT NULL DEFAULT 0,
    created_at   TIMESTAMPTZ  NOT NULL,
    updated_at   TIMESTAMPTZ  NOT NULL
);

CREATE INDEX idx_game_visibility_status ON game (visibility, status);
CREATE UNIQUE INDEX idx_game_invite_code ON game (invite_code) WHERE invite_code IS NOT NULL;
CREATE INDEX idx_game_player_a ON game (player_a);
CREATE INDEX idx_game_player_b ON game (player_b);
CREATE INDEX idx_game_status_updated_at ON game (status, updated_at);

-- Chat folgt in Phase 6; die Tabelle gehört bereits zum Grundschema.
CREATE TABLE chat_message (
    id         UUID PRIMARY KEY,
    game_id    UUID         NOT NULL REFERENCES game (id) ON DELETE CASCADE,
    player_id  UUID         NOT NULL REFERENCES player (id),
    text       VARCHAR(500) NOT NULL,
    created_at TIMESTAMPTZ  NOT NULL
);

CREATE INDEX idx_chat_message_game ON chat_message (game_id, created_at);
