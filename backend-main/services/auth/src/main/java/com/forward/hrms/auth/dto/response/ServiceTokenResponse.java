package com.forward.hrms.auth.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;

import java.time.Instant;

@Schema(name = "ServiceTokenResponse",
        description = "Long-lived service-account JWT. Surfaced once at issue time. Auth does not store it; " +
                "if lost, mint a new one and rotate the consumer's secret.")
public record ServiceTokenResponse(
        @Schema(description = "Bearer token. RS256, identical claim shape to a login JWT plus `tokenType: \"service\"`.",
                requiredMode = Schema.RequiredMode.REQUIRED)
        String token,

        @Schema(description = "Token expiry as an ISO-8601 instant. Equal to issued-at + ttlDays.",
                example = "2027-05-08T13:42:00Z",
                requiredMode = Schema.RequiredMode.REQUIRED)
        Instant expiresAt,

        @Schema(description = "JWT id (jti). Useful for grepping audit logs after the fact.",
                requiredMode = Schema.RequiredMode.REQUIRED)
        String jti
) {}
