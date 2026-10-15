package com.forward.hrms.notification.push;

import org.junit.jupiter.api.Test;

import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.security.Signature;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class VapidSignerTest {

    // Key pair from RFC 8291 Appendix A (application server).
    private static final String PUBLIC = "BP4z9KsN6nGRTbVYI_c7VJSPQTBtkgcy27mlmlMoZIIgDll6e3vCYLocInmYWAmS6TlzAC8wEqKK6PBru3jl7A8";
    private static final String PRIVATE = "yfWPiYE-n46HLnH0KqZOF1fJJU3MYrct3AELtAQ-oRw";

    @Test
    void signsAnEs256TokenScopedToThePushServiceOrigin() throws Exception {
        VapidSigner signer = new VapidSigner(PUBLIC, PRIVATE, "mailto:it@forwardsports.com");

        String header = signer.authorization(URI.create("https://fcm.googleapis.com/fcm/send/abc:def"));

        assertThat(header).startsWith("vapid t=").endsWith(", k=" + PUBLIC);
        String jwt = header.substring("vapid t=".length(), header.indexOf(", k="));
        String[] parts = jwt.split("\\.");
        assertThat(parts).hasSize(3);

        String claims = new String(Base64.getUrlDecoder().decode(parts[1]), StandardCharsets.UTF_8);
        assertThat(claims).contains("\"aud\":\"https://fcm.googleapis.com\"")
                .contains("\"sub\":\"mailto:it@forwardsports.com\"");
        long exp = Long.parseLong(claims.replaceAll(".*\"exp\":(\\d+).*", "$1"));
        assertThat(exp).isBetween(Instant.now().getEpochSecond(), Instant.now().plus(Duration.ofHours(24)).getEpochSecond());

        Signature verify = Signature.getInstance("SHA256withECDSAinP1363Format");
        verify.initVerify(WebPushCrypto.decodePublicKey(WebPushCrypto.b64(PUBLIC)));
        verify.update((parts[0] + "." + parts[1]).getBytes(StandardCharsets.US_ASCII));
        assertThat(verify.verify(Base64.getUrlDecoder().decode(parts[2]))).isTrue();
    }

    @Test
    void rejectsAMalformedKeyPairAtStartup() {
        assertThatThrownBy(() -> new VapidSigner("abc", PRIVATE, "mailto:x@y.z"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void onlyAllowListedHttpsPushHostsAreContacted() {
        WebPushClient client = new WebPushClient(new VapidSigner(PUBLIC, PRIVATE, "mailto:x@y.z"),
                Duration.ofHours(1), List.of("fcm.googleapis.com", "notify.windows.com"));

        assertThat(client.isAllowedEndpoint("https://fcm.googleapis.com/fcm/send/x")).isTrue();
        assertThat(client.isAllowedEndpoint("https://wns2-bl2p.notify.windows.com/w/?token=x")).isTrue();
        assertThat(client.isAllowedEndpoint("http://fcm.googleapis.com/fcm/send/x")).isFalse();
        assertThat(client.isAllowedEndpoint("https://10.10.100.50:8084/notifications")).isFalse();
        assertThat(client.isAllowedEndpoint("https://evilfcm.googleapis.com.attacker.io/x")).isFalse();
        assertThat(client.isAllowedEndpoint("https://notfcm.googleapis.com/x")).isFalse();
        assertThat(client.isAllowedEndpoint("not a url")).isFalse();
    }
}
