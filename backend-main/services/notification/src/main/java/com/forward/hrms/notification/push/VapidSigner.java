package com.forward.hrms.notification.push;

import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.Signature;
import java.security.interfaces.ECPrivateKey;
import java.time.Duration;
import java.time.Instant;

/**
 * VAPID (RFC 8292) {@code Authorization} header: an ES256 JWT scoped to the
 * push service's origin, plus our public key.
 */
public final class VapidSigner {

    /** Push services reject tokens valid for more than 24h. */
    private static final Duration TOKEN_TTL = Duration.ofHours(12);
    private static final String HEADER = WebPushCrypto.b64(
            "{\"typ\":\"JWT\",\"alg\":\"ES256\"}".getBytes(StandardCharsets.UTF_8));

    private final String publicKey;
    private final ECPrivateKey privateKey;
    private final String subject;

    public VapidSigner(String publicKey, String privateKey, String subject) {
        try {
            // Round-trip the public key so a malformed config fails at startup.
            this.publicKey = WebPushCrypto.b64(WebPushCrypto.encodePoint(
                    WebPushCrypto.decodePublicKey(WebPushCrypto.b64(publicKey))));
            this.privateKey = WebPushCrypto.decodePrivateKey(WebPushCrypto.b64(privateKey));
        } catch (GeneralSecurityException | IllegalArgumentException e) {
            throw new IllegalArgumentException("invalid VAPID key pair: " + e.getMessage(), e);
        }
        this.subject = subject;
    }

    /** {@code vapid t=<jwt>, k=<public key>} for a request to {@code endpoint}. */
    public String authorization(URI endpoint) {
        String audience = endpoint.getScheme() + "://" + endpoint.getHost()
                + (endpoint.getPort() == -1 ? "" : ":" + endpoint.getPort());
        long exp = Instant.now().plus(TOKEN_TTL).getEpochSecond();
        String claims = WebPushCrypto.b64(("{\"aud\":\"" + audience + "\",\"exp\":" + exp
                + ",\"sub\":\"" + subject + "\"}").getBytes(StandardCharsets.UTF_8));
        String signingInput = HEADER + "." + claims;
        try {
            // P1363 = raw r||s, the JWS encoding (not DER).
            Signature sig = Signature.getInstance("SHA256withECDSAinP1363Format");
            sig.initSign(privateKey);
            sig.update(signingInput.getBytes(StandardCharsets.US_ASCII));
            return "vapid t=" + signingInput + "." + WebPushCrypto.b64(sig.sign()) + ", k=" + publicKey;
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("VAPID signing failed", e);
        }
    }
}
