package de.pwe.schiffeversenken.domain;

/**
 * Seite eines Spielers im Spiel. A erstellt das Spiel, B tritt bei.
 */
public enum PlayerSide {
    A,
    B;

    public PlayerSide opponent() {
        return this == A ? B : A;
    }
}
