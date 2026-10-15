package com.forward.hrms.notification.push;

import org.junit.jupiter.api.Test;

import javax.crypto.Cipher;
import javax.crypto.KeyAgreement;
import javax.crypto.Mac;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.interfaces.ECPublicKey;
import java.util.Arrays;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class WebPushCryptoTest {

    // RFC 8291 Appendix A.
    private static final String PLAINTEXT = "When I grow up, I want to be a watermelon";
    private static final String AS_PUBLIC = "BP4z9KsN6nGRTbVYI_c7VJSPQTBtkgcy27mlmlMoZIIgDll6e3vCYLocInmYWAmS6TlzAC8wEqKK6PBru3jl7A8";
    private static final String AS_PRIVATE = "yfWPiYE-n46HLnH0KqZOF1fJJU3MYrct3AELtAQ-oRw";
    private static final String UA_PUBLIC = "BCVxsr7N_eNgVRqvHtD0zTZsEc6-VV-JvLexhqUzORcxaOzi6-AYWXvTBHm4bjyPjs7Vd8pZGH6SRpkNtoIAiw4";
    private static final String UA_PRIVATE = "q1dXpw3UpT5VOmu_cf_v6ih07Aems3njxI-JWgLcM94";
    private static final String SALT = "DGv6ra1nlYgDCS1FRnbzlw";
    private static final String AUTH = "BTBZMqHH6r4Tts7J_aSIgg";
    private static final String EXPECTED =
            "DGv6ra1nlYgDCS1FRnbzlwAAEABBBP4z9KsN6nGRTbVYI_c7VJSPQTBtkgcy27mlmlMoZIIgDll6e3vCYLocInmYWAmS6TlzAC8wEqKK6PBru3jl7A_yl95bQpu6cVPTpK4Mqgkf1CXztLVBSt2Ks3oZwbuwXPXLWyouBWLVWGNWQexSgSxsj_Qulcy4a-fN";

    @Test
    void matchesRfc8291TestVector() throws Exception {
        KeyPair as = new KeyPair(
                WebPushCrypto.decodePublicKey(WebPushCrypto.b64(AS_PUBLIC)),
                WebPushCrypto.decodePrivateKey(WebPushCrypto.b64(AS_PRIVATE)));

        byte[] out = WebPushCrypto.encrypt(PLAINTEXT.getBytes(StandardCharsets.UTF_8),
                WebPushCrypto.b64(UA_PUBLIC), WebPushCrypto.b64(AUTH), as, WebPushCrypto.b64(SALT));

        assertThat(WebPushCrypto.b64(out)).isEqualTo(EXPECTED);
    }

    @Test
    void browserCanDecryptARandomisedMessage() throws Exception {
        KeyPairGenerator gen = KeyPairGenerator.getInstance("EC");
        gen.initialize(WebPushCrypto.P256);
        KeyPair ua = gen.generateKeyPair();
        byte[] uaPublic = WebPushCrypto.encodePoint((ECPublicKey) ua.getPublic());
        byte[] auth = new byte[16];
        new java.security.SecureRandom().nextBytes(auth);
        byte[] plaintext = "{\"title\":\"Leave approved\"}".getBytes(StandardCharsets.UTF_8);

        byte[] message = WebPushCrypto.encrypt(plaintext, uaPublic, auth);

        assertThat(decrypt(message, ua, uaPublic, auth)).isEqualTo(plaintext);
    }

    @Test
    void rejectsOversizedPayload() {
        byte[] tooBig = new byte[WebPushCrypto.MAX_PLAINTEXT + 1];
        assertThatThrownBy(() -> WebPushCrypto.encrypt(tooBig,
                WebPushCrypto.b64(UA_PUBLIC), WebPushCrypto.b64(AUTH)))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void validatesBrowserKeys() {
        assertThat(WebPushCrypto.isValidPublicKey(UA_PUBLIC)).isTrue();
        assertThat(WebPushCrypto.isValidPublicKey(AUTH)).isFalse();
        assertThat(WebPushCrypto.isValidPublicKey("not base64 !")).isFalse();
    }

    /** User-agent side of RFC 8291, written independently of the encryptor. */
    private static byte[] decrypt(byte[] message, KeyPair ua, byte[] uaPublic, byte[] auth) throws Exception {
        ByteBuffer buf = ByteBuffer.wrap(message);
        byte[] salt = new byte[16];
        buf.get(salt);
        buf.getInt(); // record size
        byte[] asPublic = new byte[buf.get()];
        buf.get(asPublic);
        byte[] ciphertext = new byte[buf.remaining()];
        buf.get(ciphertext);

        KeyAgreement ka = KeyAgreement.getInstance("ECDH");
        ka.init(ua.getPrivate());
        ka.doPhase(WebPushCrypto.decodePublicKey(asPublic), true);
        byte[] prkKey = hmac(auth, ka.generateSecret());
        byte[] ikm = hmac(prkKey, cat("WebPush: info\0".getBytes(StandardCharsets.US_ASCII), uaPublic, asPublic, new byte[]{1}));
        byte[] prk = hmac(salt, ikm);
        byte[] cek = Arrays.copyOf(hmac(prk, cat("Content-Encoding: aes128gcm\0".getBytes(StandardCharsets.US_ASCII), new byte[]{1})), 16);
        byte[] nonce = Arrays.copyOf(hmac(prk, cat("Content-Encoding: nonce\0".getBytes(StandardCharsets.US_ASCII), new byte[]{1})), 12);

        Cipher c = Cipher.getInstance("AES/GCM/NoPadding");
        c.init(Cipher.DECRYPT_MODE, new SecretKeySpec(cek, "AES"), new GCMParameterSpec(128, nonce));
        byte[] padded = c.doFinal(ciphertext);
        assertThat(padded[padded.length - 1]).isEqualTo((byte) 2);
        return Arrays.copyOf(padded, padded.length - 1);
    }

    private static byte[] hmac(byte[] key, byte[] data) throws Exception {
        Mac mac = Mac.getInstance("HmacSHA256");
        mac.init(new SecretKeySpec(key, "HmacSHA256"));
        return mac.doFinal(data);
    }

    private static byte[] cat(byte[]... parts) {
        ByteBuffer b = ByteBuffer.allocate(Arrays.stream(parts).mapToInt(p -> p.length).sum());
        for (byte[] p : parts) b.put(p);
        return b.array();
    }
}
