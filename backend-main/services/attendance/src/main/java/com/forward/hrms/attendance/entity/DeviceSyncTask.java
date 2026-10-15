package com.forward.hrms.attendance.entity;

import com.forward.hrms.attendance.enums.DeviceSyncTaskStatus;
import com.forward.hrms.attendance.enums.DeviceSyncTaskType;
import com.forward.hrms.commons.core.jpa.AuditedEntity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

import org.hibernate.annotations.ColumnTransformer;

import java.time.LocalDateTime;

/**
 * Outbox-style queue row drained by the attendance-poller sidecar.
 * One row per (device, action). status moves
 * PENDING → IN_PROGRESS → DONE | FAILED via the poller-facing REST
 * surface. payload is jsonb (per task_type — see the V0008 SQL
 * comment for the shape); stored as a String here and round-tripped
 * via Jackson in the service layer to keep the entity simple.
 */
@Entity
@Table(name = "attendance_device_sync_tasks")
@Getter
@Setter
public class DeviceSyncTask extends AuditedEntity {

    @Column(name = "device_id", nullable = false)
    private Long deviceId;

    @Column(name = "employee_id", nullable = false)
    private Long employeeId;

    @Enumerated(EnumType.STRING)
    @Column(name = "task_type", nullable = false, length = 24)
    private DeviceSyncTaskType taskType;

    @Column(name = "payload", nullable = false, columnDefinition = "jsonb")
    @ColumnTransformer(write = "?::jsonb")
    private String payload;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 16)
    private DeviceSyncTaskStatus status = DeviceSyncTaskStatus.PENDING;

    @Column(name = "attempts", nullable = false)
    private int attempts = 0;

    @Column(name = "last_error")
    private String lastError;

    @Column(name = "scheduled_for", nullable = false)
    private LocalDateTime scheduledFor;

    @Column(name = "claimed_at")
    private LocalDateTime claimedAt;

    @Column(name = "completed_at")
    private LocalDateTime completedAt;
}
