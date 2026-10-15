package com.forward.hrms.auth.jwt;

import lombok.extern.slf4j.Slf4j;

import org.springframework.core.io.Resource;
import org.springframework.util.StreamUtils;

import java.nio.charset.StandardCharsets;
import java.security.KeyFactory;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.MessageDigest;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.security.interfaces.RSAPublicKey;
import java.security.spec.PKCS8EncodedKeySpec;
import java.security.spec.X509EncodedKeySpec;
import java.util.Base64;
import java.util.HexFormat;

/**
 * Loads the RSA keypair the issuer uses to sign access tokens and the
 * JWKS endpoint exposes for verifiers. Falls back to a generated 2048-bit
 * keypair if no PEM is configured — dev-only convenience.
 */
@Slf4j
public class AuthKeys {

    private final RSAPublicKey publicKey;
    private final PrivateKey privateKey;
    private final String keyId;

    public AuthKeys(AuthJwtProperties props) {
        try {
            String privPem = readPem(props.privateKey(), props.privateKeyLocation());
            String pubPem = readPem(props.publicKey(), props.publicKeyLocation());

            if (privPem == null || pubPem == null) {
                log.warn("hrms.auth.jwt.private-key/public-key not configured — generating an in-memory RSA keypair. "
                        + "Tokens will be invalidated on restart. Configure keys for UAT/prod.");
                KeyPair kp = generate();
                this.privateKey = kp.getPrivate();
                this.publicKey = (RSAPublicKey) kp.getPublic();
            } else {
                this.privateKey = parsePrivateKey(privPem);
                this.publicKey = (RSAPublicKey) parsePublicKey(pubPem);
            }
            this.keyId = computeKid(this.publicKey);
        } catch (RuntimeException e) {
            throw e;
        } catch (Exception e) {
            throw new IllegalStateException("Failed to initialize auth signing keys", e);
        }
    }

    public RSAPublicKey publicKey() { return publicKey; }
    public PrivateKey privateKey() { return privateKey; }
    public String keyId() { return keyId; }

    private static KeyPair generate() throws Exception {
        KeyPairGenerator g = KeyPairGenerator.getInstance("RSA");
        g.initialize(2048);
        return g.generateKeyPair();
    }

    private static String readPem(String inline, Resource location) throws Exception {
        if (inline != null && !inline.isBlank()) return inline;
        if (location != null && location.exists()) {
            return new String(StreamUtils.copyToByteArray(location.getInputStream()), StandardCharsets.UTF_8);
        }
        return null;
    }

    private static byte[] decodePem(String pem) {
        String body = pem.replaceAll("-----BEGIN [A-Z ]+-----", "")
                .replaceAll("-----END [A-Z ]+-----", "")
                .replaceAll("\\s+", "");
        return Base64.getDecoder().decode(body);
    }

    private static PrivateKey parsePrivateKey(String pem) throws Exception {
        return KeyFactory.getInstance("RSA").generatePrivate(new PKCS8EncodedKeySpec(decodePem(pem)));
    }

    private static PublicKey parsePublicKey(String pem) throws Exception {
        return KeyFactory.getInstance("RSA").generatePublic(new X509EncodedKeySpec(decodePem(pem)));
    }

    private static String computeKid(RSAPublicKey key) throws Exception {
        byte[] digest = MessageDigest.getInstance("SHA-256").digest(key.getEncoded());
        return HexFormat.of().formatHex(digest, 0, 8);
    }
}
