package com.forward.hrms.audit.dto.response;

import com.forward.hrms.audit.entity.AuditDeadLetter;
import com.forward.hrms.audit.enums.SkipReason;

import io.swagger.v3.oas.annotations.media.Schema;

import java.time.LocalDateTime;

@Schema(description = "Audit envelope that ingest rejected. Held until an operator replays or discards.")
public record AuditDeadLetterDto(
        @Schema(description = "Dead-letter row id.", example = "42") Long id,
        @Schema(description = "Service that emitted the envelope.", example = "auth") String sourceService,
        @Schema(description = "Source Kafka topic.", example = "auth.audit") String topic,
        @Schema(description = "Source Kafka partition.", example = "2") int partition,
        @Schema(description = "Source Kafka offset.", example = "184273") long offset,
        @Schema(description = "Source Kafka key.", example = "user-1042", nullable = true) String key,
        @Schema(description = "Why ingest rejected this envelope.", example = "UNPARSEABLE_OCCURRED_AT") SkipReason reason,
        @Schema(description = "Free-form detail captured at rejection.", nullable = true) String reasonDetail,
        @Schema(description = "When the consumer parked the envelope.") LocalDateTime receivedAt,
        @Schema(description = "Last time an operator hit /replay.", nullable = true) LocalDateTime lastReplayAt,
        @Schema(description = "Number of replay attempts.", example = "0") int replayAttempts,
        @Schema(description = "Original envelope JSON (truncated above 1MB).") String rawPayload
) {

    /** Default projection — full row including raw payload. */
    public static AuditDeadLetterDto of(AuditDeadLetter d) {
        return new AuditDeadLetterDto(
                d.getId(),
                d.getSourceService(),
                d.getTopic(),
                d.getKafkaPartition(),
                d.getKafkaOffset(),
                d.getKafkaKey(),
                d.getReason(),
                d.getReasonDetail(),
                d.getReceivedAt(),
                d.getLastReplayAt(),
                d.getReplayAttempts(),
                d.getRawPayload()
        );
    }

    /**
     * Listing projection — drops raw payload to keep page responses
     * small (raw is up to 1 MB; a page of 50 would be 50 MB).
     * Operators fetch the raw via {@code GET /admin/audit/dead-letters/{id}}.
     */
    public static AuditDeadLetterDto summary(AuditDeadLetter d) {
        return new AuditDeadLetterDto(
                d.getId(),
                d.getSourceService(),
                d.getTopic(),
                d.getKafkaPartition(),
                d.getKafkaOffset(),
                d.getKafkaKey(),
                d.getReason(),
                d.getReasonDetail(),
                d.getReceivedAt(),
                d.getLastReplayAt(),
                d.getReplayAttempts(),
                null
        );
    }
}
