package com.forward.hrms.notification.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;
import java.util.List;

/**
 * Web Push (VAPID) settings. Push is off until both keys are set — the
 * config endpoint then reports {@code enabled=false} and the frontend hides
 * the opt-in. Generate a pair with {@code npx web-push generate-vapid-keys}.
 *
 * @param publicKey    VAPID public key, base64url uncompressed P-256 point (served to browsers)
 * @param privateKey   VAPID private key, base64url 32-byte scalar (secret)
 * @param subject      contact the push services can reach us at ({@code mailto:} or {@code https:})
 * @param ttl          how long a push service keeps an undelivered message
 * @param allowedHosts push-service host suffixes we will POST to; guards
 *                     against a client registering an internal URL as its endpoint
 */
@ConfigurationProperties(prefix = "hrms.notification.push")
public record PushProperties(
        String publicKey,
        String privateKey,
        String subject,
        Duration ttl,
        List<String> allowedHosts
) {
    public PushProperties {
        if (subject == null || subject.isBlank()) subject = "mailto:it@forwardsports.com";
        if (ttl == null) ttl = Duration.ofHours(24);
        if (allowedHosts == null || allowedHosts.isEmpty()) {
            allowedHosts = List.of(
                    "fcm.googleapis.com",          // Chrome, Android, Opera, Brave
                    "push.services.mozilla.com",   // Firefox
                    "push.apple.com",              // Safari (macOS, iOS home-screen app)
                    "notify.windows.com");         // Edge
        }
    }

    public boolean enabled() {
        return publicKey != null && !publicKey.isBlank()
                && privateKey != null && !privateKey.isBlank();
    }
}
