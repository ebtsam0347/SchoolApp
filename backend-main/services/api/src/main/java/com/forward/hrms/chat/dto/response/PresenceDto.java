package com.forward.hrms.chat.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;

import java.time.LocalDateTime;

@Schema(name = "PresenceDto", description = "A user's connection status. `online` means we have a live SSE within the last ~30s; otherwise `offline` with a `lastSeenAt` if known.")
public record PresenceDto(
        long userId,
        @Schema(description = "online | offline") String status,
        @Schema(nullable = true) LocalDateTime lastSeenAt
) {}
