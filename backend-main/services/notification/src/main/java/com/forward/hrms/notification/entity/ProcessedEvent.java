package com.forward.hrms.notification.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

/**
 * Idempotency table — durable backstop for dedupe of cross-service Kafka
 * events. Redis is the hot path; this table catches the case where Redis
 * loses the entry (eviction, restart) but Kafka redelivers.
 */
@Entity
@Table(name = "notification_processed_events")
@Getter
@Setter
public class ProcessedEvent {

    @Id
    @Column(name = "event_id", length = 64, nullable = false)
    private String eventId;

    @Column(name = "source_topic", length = 64, nullable = false)
    private String sourceTopic;

    @Column(name = "event_type", length = 64, nullable = false)
    private String eventType;

    @Column(name = "processed_at", nullable = false)
    private LocalDateTime processedAt;
}
