package com.forward.hrms.attendance.entity;

import com.forward.hrms.commons.core.context.CurrentUserHolder;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

import org.hibernate.annotations.ColumnTransformer;

import java.time.LocalDateTime;

/**
 * Per-device polling state owned by attendance-service. Replaces the
 * poller's local {@code ~/.attendance-poller/state.json}. The poller
 * fetches {@code lastPunchedAt} via {@code GET /devices/poller-targets},
 * sends only newer punches, and the service advances the high-water-mark
 * inside the same transaction as the punch insert.
 *
 * <p>PK is {@code device_id} (not a synthetic id) — exactly one row per
 * device, lifecycle tied to the device row via {@code ON DELETE CASCADE}.
 */
@Entity
@Table(name = "attendance_device_poll_state")
@Getter
@Setter
public class DevicePollState {

    @Id
    @Column(name = "device_id", nullable = false, updatable = false)
    private Long deviceId;

    @Column(name = "last_punched_at")
    private LocalDateTime lastPunchedAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    @Column(name = "created_by", nullable = false, updatable = false, length = 64)
    private String createdBy;

    @Column(name = "updated_by", nullable = false, length = 64)
    private String updatedBy;

    @Column(name = "ip_address", columnDefinition = "inet")
    @ColumnTransformer(write = "?::inet")
    private String ipAddress;

    // PK is device_id (not a synthetic id), so this entity does not extend
    // AuditedEntity — its listener takes an AuditedEntity parameter and
    // would IllegalArgumentException on the reflection call. Audit fields
    // are still populated, just inline.
    @PrePersist
    void prePersist() {
        LocalDateTime now = LocalDateTime.now();
        String user = CurrentUserHolder.usernameOrSystem();
        if (createdAt == null) createdAt = now;
        updatedAt = now;
        if (createdBy == null) createdBy = user;
        updatedBy = user;
    }

    @PreUpdate
    void preUpdate() {
        updatedAt = LocalDateTime.now();
        updatedBy = CurrentUserHolder.usernameOrSystem();
    }
}
