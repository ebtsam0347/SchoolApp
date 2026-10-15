package com.forward.hrms.notification.push;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.List;
import java.util.Locale;

/**
 * Sends one encrypted message to one push-service endpoint (RFC 8030).
 * Only hosts on the allow-list are contacted.
 */
public final class WebPushClient {

    private final HttpClient http = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(5))
            .followRedirects(HttpClient.Redirect.NEVER)
            .build();
    private final VapidSigner vapid;
    private final Duration ttl;
    private final List<String> allowedHosts;

    public WebPushClient(VapidSigner vapid, Duration ttl, List<String> allowedHosts) {
        this.vapid = vapid;
        this.ttl = ttl;
        this.allowedHosts = allowedHosts.stream().map(h -> h.toLowerCase(Locale.ROOT)).toList();
    }

    /** True for an {@code https} URL on an allow-listed push-service host (or a subdomain of one). */
    public boolean isAllowedEndpoint(String endpoint) {
        try {
            URI uri = URI.create(endpoint);
            if (!"https".equalsIgnoreCase(uri.getScheme()) || uri.getHost() == null) return false;
            String host = uri.getHost().toLowerCase(Locale.ROOT);
            return allowedHosts.stream().anyMatch(h -> host.equals(h) || host.endsWith("." + h));
        } catch (IllegalArgumentException e) {
            return false;
        }
    }

    /**
     * Encrypt and POST {@code payload}. Returns the push service's HTTP
     * status: 201 accepted; 404/410 mean the subscription is gone.
     */
    public int send(String endpoint, String p256dh, String auth, byte[] payload, boolean urgent)
            throws IOException, InterruptedException {
        if (!isAllowedEndpoint(endpoint)) {
            throw new IllegalArgumentException("push endpoint host not allowed");
        }
        URI uri = URI.create(endpoint);
        byte[] body = WebPushCrypto.encrypt(payload, WebPushCrypto.b64(p256dh), WebPushCrypto.b64(auth));
        HttpRequest req = HttpRequest.newBuilder(uri)
                .timeout(Duration.ofSeconds(10))
                .header("Authorization", vapid.authorization(uri))
                .header("TTL", Long.toString(ttl.toSeconds()))
                .header("Urgency", urgent ? "high" : "normal")
                .header("Content-Encoding", "aes128gcm")
                .header("Content-Type", "application/octet-stream")
                .POST(HttpRequest.BodyPublishers.ofByteArray(body))
                .build();
        return http.send(req, HttpResponse.BodyHandlers.discarding()).statusCode();
    }
}
