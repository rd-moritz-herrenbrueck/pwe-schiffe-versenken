package de.pwe.schiffeversenken.service;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PlayerTokensTest {

    @Test
    void hashIsAStableSha256HexString() {
        assertEquals("2cf24dba5fb0a30e26e83b2ac5b9e29e1b161e5c1fa7425e73043362938b9824", PlayerTokens.hash("hello"));
    }

    @Test
    void differentTokensHaveDifferentHashes() {
        assertNotEquals(PlayerTokens.hash("token-1"), PlayerTokens.hash("token-2"));
    }

    @Test
    void generatedTokenIsUrlSafeAndLongEnough() {
        String token = PlayerTokens.generate();

        assertTrue(token.matches("[A-Za-z0-9_-]{43,}"), token);
    }
}
