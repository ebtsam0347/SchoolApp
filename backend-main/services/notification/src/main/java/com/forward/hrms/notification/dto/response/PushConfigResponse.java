package com.forward.hrms.notification.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "Whether Web Push is available and the key browsers subscribe with.")
public record PushConfigResponse(
        @Schema(description = "False until VAPID keys are configured; the UI hides the opt-in.")
        boolean enabled,
        @Schema(description = "VAPID public key (base64url) for PushManager.subscribe applicationServerKey. Null when disabled.")
        String publicKey
) {
}
