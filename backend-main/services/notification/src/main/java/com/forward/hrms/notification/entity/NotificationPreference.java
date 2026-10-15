package com.forward.hrms.notification.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.IdClass;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

/**
 * Per-user opt-out per notification type. Bridge entity — no audit columns
 * by convention. Default for any (user, type) pair not present is "enabled"
 * (treated as opt-in). Toggling an explicit row to {@code enabled = false}
 * tells the dispatcher to skip persistence + publish for that type.
 */
@Entity
@Table(name = "notification_preferences")
@IdClass(NotificationPreferenceId.class)
@Getter
@Setter
public class NotificationPreference {

    @Id
    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Id
    @Column(name = "type", nullable = false, length = 64)
    private String type;

    @Column(name = "enabled", nullable = false)
    private boolean enabled = true;
}
