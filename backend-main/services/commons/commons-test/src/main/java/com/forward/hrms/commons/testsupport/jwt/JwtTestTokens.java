package com.forward.hrms.commons.testsupport.jwt;

import io.jsonwebtoken.Jwts;

import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.PublicKey;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.List;
import java.util.Map;

/**
 * Generates RS256-signed access tokens for tests. Use {@link #publicKeyPem()}
 * to feed {@code hrms.jwt.public-key} so the service-under-test verifies
 * with the matching key.
 *
 * <p>callers: 1 — {@code gateway}'s {@code ApprovalsInboxRouteOrderingTest}.
 */
public final class JwtTestTokens {

    private final KeyPair keyPair;

    public JwtTestTokens() {
        try {
            KeyPairGenerator g = KeyPairGenerator.getInstance("RSA");
            g.initialize(2048);
            this.keyPair = g.generateKeyPair();
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    public String issue(long userId, String username, List<String> roles, List<String> permissions) {
        return issue(userId, username, roles, permissions, Duration.ofMinutes(15));
    }

    public String issue(long userId, String username, List<String> roles, List<String> permissions, Duration ttl) {
        return issue(userId, username, roles, permissions, ttl, null, null);
    }

    /**
     * Same as {@link #issue(long, String, List, List, Duration)} but also
     * sets {@code iss}/{@code aud} — needed against services that configure
     * {@code hrms.jwt.issuer} / {@code hrms.jwt.audience} (auth mints real
     * tokens with both; the gateway requires them when set).
     */
    public String issue(long userId, String username, List<String> roles, List<String> permissions, Duration ttl,
                         String issuer, String audience) {
        Instant now = Instant.now();
        var builder = Jwts.builder()
                .subject(String.valueOf(userId))
                .claims(Map.of(
                        "username", username,
                        "roles", roles,
                        "permissions", permissions))
                .issuedAt(java.util.Date.from(now))
                .expiration(java.util.Date.from(now.plus(ttl)));
        if (issuer != null) builder.issuer(issuer);
        if (audience != null) builder.audience().add(audience).and();
        return builder.signWith(keyPair.getPrivate(), Jwts.SIG.RS256).compact();
    }

    public PublicKey publicKey() { return keyPair.getPublic(); }

    public String publicKeyPem() {
        String b64 = Base64.getMimeEncoder(64, "\n".getBytes()).encodeToString(keyPair.getPublic().getEncoded());
        return "-----BEGIN PUBLIC KEY-----\n" + b64 + "\n-----END PUBLIC KEY-----\n";
    }

    public String authorizationHeader(long userId, String username, List<String> roles, List<String> permissions) {
        return "Bearer " + issue(userId, username, roles, permissions);
    }
}
