package com.forward.hrms.notification.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** Flattened {@code PushSubscription.toJSON()} from the browser. */
@Schema(description = "A browser Web Push subscription.")
public record PushSubscribeRequest(
        @Schema(description = "Push-service URL from PushSubscription.endpoint.")
        @NotBlank @Size(max = 1024) String endpoint,
        @Schema(description = "keys.p256dh — browser ECDH public key, base64url.")
        @NotBlank @Size(max = 128) String p256dh,
        @Schema(description = "keys.auth — browser auth secret, base64url.")
        @NotBlank @Size(max = 64) String auth,
        @Schema(description = "navigator.userAgent, for telling devices apart.")
        @Size(max = 512) String userAgent
) {
}
