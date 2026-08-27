package com.flash.fulfill.common.security;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SessionKeysTest {

    @Test
    void keyHasPrefixAndStableHash() {
        String token = "jwt-token-001";

        String key = SessionKeys.of(token);

        assertTrue(key.startsWith("user:session:"));
        assertEquals(SessionKeys.of(token), key);
    }

    @Test
    void differentTokensProduceDifferentKeys() {
        assertNotEquals(SessionKeys.of("token-a"), SessionKeys.of("token-b"));
    }

    @Test
    void hashIs64HexChars() {
        String key = SessionKeys.of("any-token");

        assertEquals("user:session:", key.substring(0, "user:session:".length()));
        assertEquals(64, key.length() - "user:session:".length());
    }
}