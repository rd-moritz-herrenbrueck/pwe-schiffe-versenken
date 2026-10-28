package de.pwe.schiffeversenken.service;

/**
 * Erzeugt Spieler-Tokens und deren Hash. In der Datenbank liegt nur der Hash.
 */
public final class PlayerTokens {

    private PlayerTokens() {
    }

    /** Neues zufälliges, URL-sicheres Token. */
    public static String generate() {
        throw new UnsupportedOperationException("Phase 3: noch nicht implementiert");
    }

    /** SHA-256 des Tokens als Hex-String (64 Zeichen, Kleinbuchstaben). */
    public static String hash(String token) {
        throw new UnsupportedOperationException("Phase 3: noch nicht implementiert");
    }
}
