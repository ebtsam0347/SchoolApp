package com.forward.hrms.audit.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;

import java.time.LocalDateTime;

/**
 * Trimmed user-visible event for the entity timeline. The producer renders
 * {@code message}; consumers don't template anything.
 */
@Schema(description = "User-facing timeline entry. One row per `user_visible = true` audit event.")
public record TimelineEntry(
        @Schema(example = "5421")                                long id,
        @Schema(example = "updated")                             String action,
        @Schema(example = "1042", nullable = true,
                description = "Actor's user id. `actorLabel` is filled at producer time from the JWT `name` claim; null only for events emitted before that wiring landed (Step 6) or by system actors with no display name.")
        Long actorUserId,
        @Schema(example = "FS-2026-0042", nullable = true)       String actorUsername,
        @Schema(example = "Saqib Mushtaq", nullable = true)      String actorLabel,
        @Schema(example = "Phone number updated.", nullable = true) String message,
        @Schema(example = "2026-04-28T09:12:33Z")                LocalDateTime occurredAt
) {}
