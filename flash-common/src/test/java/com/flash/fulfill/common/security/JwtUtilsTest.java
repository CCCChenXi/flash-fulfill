package com.flash.fulfill.common.security;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class JwtUtilsTest {

    private static final String SECRET = "flash-fulfill-demo-secret-0123456789abcdef";
    private static final long USER_ID = 1001L;

    @Test
    void generateTokenThenParseUserIdRoundTrip() {
        JwtUtils jwt = new JwtUtils(SECRET);

        String token = jwt.generateToken(USER_ID);

        assertNotNull(token);
        assertEquals(USER_ID, jwt.parseUserId(token));
    }

    @Test
    void generateTokenWithBearerPrefixStillParses() {
        JwtUtils jwt = new JwtUtils(SECRET);

        assertEquals(USER_ID, jwt.parseUserId("Bearer " + jwt.generateToken(USER_ID)));
    }

    @Test
    void validateAcceptsOwnToken() {
        JwtUtils jwt = new JwtUtils(SECRET);

        assertTrue(jwt.validate(jwt.generateToken(USER_ID)));
    }

    @Test
    void validateRejectsTamperedToken() {
        JwtUtils jwt = new JwtUtils(SECRET);
        String token = jwt.generateToken(USER_ID);
        String tampered = token.substring(0, token.length() - 2)
                + (token.endsWith("a") ? "b" : "a");

        assertFalse(jwt.validate(tampered));
        assertThrows(Exception.class, () -> jwt.parseUserId(tampered));
    }

    @Test
    void validateRejectsGarbageAndEmpty() {
        JwtUtils jwt = new JwtUtils(SECRET);

        assertFalse(jwt.validate("not.a.jwt"));
        assertFalse(jwt.validate(""));
        assertFalse(jwt.validate(null));
    }

    @Test
    void rejectSecretShorterThan32Bytes() {
        assertThrows(IllegalArgumentException.class, () -> new JwtUtils("too-short-secret"));
    }

    @Test
    void rejectNullUserId() {
        assertThrows(IllegalArgumentException.class,
                () -> new JwtUtils(SECRET).generateToken(null));
    }
}