package com.forward.hrms.notification.push;

import javax.crypto.Cipher;
import javax.crypto.KeyAgreement;
import javax.crypto.Mac;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;

import java.math.BigInteger;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.AlgorithmParameters;
import java.security.GeneralSecurityException;
import java.security.KeyFactory;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.SecureRandom;
import java.security.interfaces.ECPrivateKey;
import java.security.interfaces.ECPublicKey;
import java.security.spec.ECGenParameterSpec;
import java.security.spec.ECParameterSpec;
import java.security.spec.ECPoint;
import java.security.spec.ECPrivateKeySpec;
import java.security.spec.ECPublicKeySpec;
import java.util.Arrays;
import java.util.Base64;

/**
 * Web Push message encryption, RFC 8291 ({@code aes128gcm}, RFC 8188), on
 * JDK crypto only. One record per message; the output is the complete
 * request body (header + ciphertext).
 */
public final class WebPushCrypto {

    /** Record size advertised in the header. One record must hold the whole message. */
    static final int RECORD_SIZE = 4096;
    /** Largest plaintext that fits one record: rs − delimiter − GCM tag. */
    public static final int MAX_PLAINTEXT = RECORD_SIZE - 1 - 16;

    private static final byte[] KEY_INFO = "WebPush: info\0".getBytes(StandardCharsets.US_ASCII);
    private static final byte[] CEK_INFO = "Content-Encoding: aes128gcm\0".getBytes(StandardCharsets.US_ASCII);
    private static final byte[] NONCE_INFO = "Content-Encoding: nonce\0".getBytes(StandardCharsets.US_ASCII);

    private static final SecureRandom RANDOM = new SecureRandom();
    static final ECParameterSpec P256 = p256();

    private WebPushCrypto() {}

    /**
     * Encrypt {@code plaintext} for one browser subscription.
     *
     * @param uaPublic   the subscription's {@code p256dh} key (65-byte uncompressed point)
     * @param authSecret the subscription's {@code auth} secret (16 bytes)
     */
    public static byte[] encrypt(byte[] plaintext, byte[] uaPublic, byte[] authSecret) {
        try {
            KeyPairGenerator gen = KeyPairGenerator.getInstance("EC");
            gen.initialize(P256, RANDOM);
            byte[] salt = new byte[16];
            RANDOM.nextBytes(salt);
            return encrypt(plaintext, uaPublic, authSecret, gen.generateKeyPair(), salt);
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("web push encryption failed", e);
        }
    }

    /** Deterministic core — the ephemeral key pair and salt are injected so tests can use the RFC vector. */
    static byte[] encrypt(byte[] plaintext, byte[] uaPublic, byte[] authSecret,
                          KeyPair asKeys, byte[] salt) throws GeneralSecurityException {
        if (plaintext.length > MAX_PLAINTEXT) {
            throw new IllegalArgumentException("push payload too large: " + plaintext.length + " bytes");
        }
        byte[] asPublic = encodePoint((ECPublicKey) asKeys.getPublic());

        KeyAgreement ka = KeyAgreement.getInstance("ECDH");
        ka.init(asKeys.getPrivate());
        ka.doPhase(decodePublicKey(uaPublic), true);
        byte[] ecdhSecret = ka.generateSecret();

        // RFC 8291 §3.3–3.4: mix the ECDH secret with the auth secret, then
        // derive the content key and nonce with the per-message salt.
        byte[] prkKey = hmac(authSecret, ecdhSecret);
        byte[] ikm = hmac(prkKey, concat(KEY_INFO, uaPublic, asPublic, new byte[]{1}));
        byte[] prk = hmac(salt, ikm);
        byte[] cek = Arrays.copyOf(hmac(prk, concat(CEK_INFO, new byte[]{1})), 16);
        byte[] nonce = Arrays.copyOf(hmac(prk, concat(NONCE_INFO, new byte[]{1})), 12);

        Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
        cipher.init(Cipher.ENCRYPT_MODE, new SecretKeySpec(cek, "AES"), new GCMParameterSpec(128, nonce));
        // 0x02 = padding delimiter of the last (and only) record.
        byte[] ciphertext = cipher.doFinal(concat(plaintext, new byte[]{2}));

        return ByteBuffer.allocate(16 + 4 + 1 + asPublic.length + ciphertext.length)
                .put(salt)
                .putInt(RECORD_SIZE)
                .put((byte) asPublic.length)
                .put(asPublic)
                .put(ciphertext)
                .array();
    }

    /** True when {@code base64url} decodes to a valid browser {@code p256dh} key. */
    public static boolean isValidPublicKey(String base64url) {
        try {
            decodePublicKey(b64(base64url));
            return true;
        } catch (GeneralSecurityException | IllegalArgumentException e) {
            return false;
        }
    }

    /** Decodes base64url, tolerating standard-alphabet input and padding. */
    public static byte[] b64(String value) {
        return Base64.getUrlDecoder().decode(
                value.trim().replace('+', '-').replace('/', '_').replace("=", ""));
    }

    static String b64(byte[] bytes) {
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    // ----- P-256 key helpers (shared with VapidSigner) -----

    static ECPublicKey decodePublicKey(byte[] uncompressed) throws GeneralSecurityException {
        if (uncompressed.length != 65 || uncompressed[0] != 0x04) {
            throw new GeneralSecurityException("expected a 65-byte uncompressed P-256 point");
        }
        ECPoint point = new ECPoint(
                new BigInteger(1, Arrays.copyOfRange(uncompressed, 1, 33)),
                new BigInteger(1, Arrays.copyOfRange(uncompressed, 33, 65)));
        return (ECPublicKey) KeyFactory.getInstance("EC").generatePublic(new ECPublicKeySpec(point, P256));
    }

    static ECPrivateKey decodePrivateKey(byte[] d) throws GeneralSecurityException {
        if (d.length != 32) throw new GeneralSecurityException("expected a 32-byte P-256 private key");
        return (ECPrivateKey) KeyFactory.getInstance("EC")
                .generatePrivate(new ECPrivateKeySpec(new BigInteger(1, d), P256));
    }

    static byte[] encodePoint(ECPublicKey key) {
        byte[] out = new byte[65];
        out[0] = 0x04;
        copyUnsigned(key.getW().getAffineX(), out, 1);
        copyUnsigned(key.getW().getAffineY(), out, 33);
        return out;
    }

    private static void copyUnsigned(BigInteger n, byte[] dst, int offset) {
        byte[] raw = n.toByteArray();
        int len = Math.min(raw.length, 32);
        System.arraycopy(raw, raw.length - len, dst, offset + 32 - len, len);
    }

    private static byte[] hmac(byte[] key, byte[] data) throws GeneralSecurityException {
        Mac mac = Mac.getInstance("HmacSHA256");
        mac.init(new SecretKeySpec(key, "HmacSHA256"));
        return mac.doFinal(data);
    }

    private static byte[] concat(byte[]... parts) {
        int len = 0;
        for (byte[] p : parts) len += p.length;
        ByteBuffer buf = ByteBuffer.allocate(len);
        for (byte[] p : parts) buf.put(p);
        return buf.array();
    }

    private static ECParameterSpec p256() {
        try {
            AlgorithmParameters params = AlgorithmParameters.getInstance("EC");
            params.init(new ECGenParameterSpec("secp256r1"));
            return params.getParameterSpec(ECParameterSpec.class);
        } catch (GeneralSecurityException e) {
            throw new ExceptionInInitializerError(e);
        }
    }
}
