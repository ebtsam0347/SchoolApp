package com.forward.hrms.notification.entity;

import com.forward.hrms.commons.core.jpa.AuditedEntity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;

import lombok.Getter;
import lombok.Setter;

/**
 * A browser's Web Push subscription. {@code endpoint} is unique — the same
 * browser re-subscribing (or another user signing in on it) updates the row
 * in place.
 */
@Entity
@Table(name = "push_subscriptions")
@Getter
@Setter
public class PushSubscription extends AuditedEntity {

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(name = "endpoint", nullable = false, length = 1024)
    private String endpoint;

    @Column(name = "p256dh", nullable = false, length = 128)
    private String p256dh;

    @Column(name = "auth", nullable = false, length = 64)
    private String auth;

    @Column(name = "user_agent", length = 512)
    private String userAgent;
}
