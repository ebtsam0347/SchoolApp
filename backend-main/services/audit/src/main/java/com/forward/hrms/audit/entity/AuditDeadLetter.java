package com.forward.hrms.audit.entity;

import com.forward.hrms.audit.enums.SkipReason;
import com.forward.hrms.commons.core.jpa.BaseLongIdEntity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

/**
 * Envelope that {@code AuditIngestService} could not persist. Held here
 * until an operator replays (after the producer is fixed) or discards.
 * Steady-state row count is zero — this table only fills while a bug is
 * open.
 */
@Entity
@Table(name = "audit_dead_letters")
@Getter
@Setter
public class AuditDeadLetter extends BaseLongIdEntity {

    @Column(name = "source_service", nullable = false, length = 32)
    private String sourceService;

    @Column(name = "topic", nullable = false, length = 64)
    private String topic;

    @Column(name = "kafka_partition", nullable = false)
    private int kafkaPartition;

    @Column(name = "kafka_offset", nullable = false)
    private long kafkaOffset;

    @Column(name = "kafka_key", length = 255)
    private String kafkaKey;

    @Column(name = "raw_payload", nullable = false)
    private String rawPayload;

    @Enumerated(EnumType.STRING)
    @Column(name = "reason", nullable = false, length = 64)
    private SkipReason reason;

    @Column(name = "reason_detail")
    private String reasonDetail;

    @Column(name = "received_at", nullable = false, updatable = false)
    private LocalDateTime receivedAt;

    @Column(name = "last_replay_at")
    private LocalDateTime lastReplayAt;

    @Column(name = "replay_attempts", nullable = false)
    private int replayAttempts;
}
