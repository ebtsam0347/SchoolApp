package com.forward.hrms.notification.entity;

import com.forward.hrms.commons.core.jpa.AuditedEntity;
import com.forward.hrms.notification.enums.NotificationCategory;
import com.forward.hrms.notification.enums.NotificationPriority;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.LocalDateTime;

/**
 * One row per (user, event). The actor whose action triggered the
 * notification populates {@code created_by} via the audit listener; for
 * system-generated entries (Kafka consumers running off a scheduled
 * thread) the actor falls back to {@code "system"}.
 */
@Entity
@Table(name = "notifications")
@Getter
@Setter
public class Notification extends AuditedEntity {

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(name = "type", nullable = false, length = 64)
    private String type;

    @Column(name = "title", nullable = false, length = 255)
    private String title;

    @Column(name = "body", columnDefinition = "text")
    private String body;

    @Column(name = "link", length = 512)
    private String link;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "payload", columnDefinition = "jsonb")
    private String payload;

    @Enumerated(EnumType.STRING)
    @Column(name = "category", length = 32)
    private NotificationCategory category;

    @Enumerated(EnumType.STRING)
    @Column(name = "priority", nullable = false, length = 16)
    private NotificationPriority priority = NotificationPriority.NORMAL;

    @Column(name = "read_at")
    private LocalDateTime readAt;
}
