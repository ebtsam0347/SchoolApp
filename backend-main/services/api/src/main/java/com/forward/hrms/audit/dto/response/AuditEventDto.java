package com.forward.hrms.audit.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;

import tools.jackson.databind.JsonNode;

import java.time.LocalDateTime;

/**
 * Wire shape for audit reads. Json fields ({@code before}, {@code after},
 * {@code changes}, {@code metadata}) are unparsed Jackson 3 {@link JsonNode}
 * instances so the client gets the original structure, whatever it is.
 *
 * <p>SB4 ships Jackson 3 ({@code tools.jackson.databind}) as the response
 * mapper. Audit's Kafka payload parsing still uses Jackson 2 (audit
 * shipped before SB4's Jackson 3 cutover) — see {@code auditObjectMapper}.
 * The two stay separate; this DTO uses Jackson 3 so the response
 * serializer renders the JsonNodes properly.
 */
@Schema(description = "An audit event row. JSON fields are returned as-is from the emitting service.")
public record AuditEventDto(
        @Schema(example = "5421")                            long id,
        @Schema(example = "01HZK7G6Y0PSV2TPC9N9XSH3WC")      String eventId,
        @Schema(example = "employee")                        String sourceService,
        @Schema(example = "employee")                        String entityType,
        @Schema(example = "1057")                            String entityId,
        @Schema(example = "updated")                         String action,
        @Schema(example = "42", nullable = true)             Long actorUserId,
        @Schema(example = "FS-2026-0042", nullable = true)   String actorUsername,
        @Schema(example = "Saqib Mushtaq", nullable = true)  String actorLabel,
        @Schema(description = "Snapshot before the change.", nullable = true) JsonNode before,
        @Schema(description = "Snapshot after the change.",  nullable = true) JsonNode after,
        @Schema(description = "Pre-diffed { field: { from, to } } if the emitter supplied it.",
                nullable = true) JsonNode changes,
        @Schema(description = "Producer metadata (ip, user-agent, source).", nullable = true) JsonNode metadata,
        @Schema(example = "8c2cc4e0-...")                    String correlationId,
        @Schema(example = "203.0.113.5", nullable = true)    String ipAddress,
        @Schema(example = "true")                            boolean userVisible,
        @Schema(example = "Phone number updated.", nullable = true) String message,
        @Schema(example = "2026-04-28T09:12:33Z")            LocalDateTime occurredAt,
        @Schema(example = "2026-04-28T09:12:34Z")            LocalDateTime ingestedAt
) {}
