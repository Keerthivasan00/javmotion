package com.javmotion.api.execution;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.HexFormat;

/**
 * Per-execution access tokens for anonymous users (doc §7.1). The raw token goes to the client
 * once; only its SHA-256 is stored, so a database leak does not expose working tokens.
 */
final class AccessTokens {

    private static final SecureRandom RANDOM = new SecureRandom();

    private AccessTokens() {}

    static String generate() {
        byte[] bytes = new byte[32];
        RANDOM.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    /** 64 hex chars — fits {@code executions.access_token_hash CHAR(64)}. */
    static String hash(String token) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(token.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }

    /** Constant-time comparison of a presented token against a stored hash. */
    static boolean matches(String presentedToken, String storedHash) {
        if (presentedToken == null || storedHash == null) return false;
        return MessageDigest.isEqual(
                hash(presentedToken).getBytes(StandardCharsets.US_ASCII), storedHash.getBytes(StandardCharsets.US_ASCII));
    }
}
