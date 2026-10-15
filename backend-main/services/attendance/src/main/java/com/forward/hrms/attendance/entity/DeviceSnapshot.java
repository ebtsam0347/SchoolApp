package com.forward.hrms.attendance.entity;

import com.forward.hrms.attendance.enums.DeviceSnapshotStatus;
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
 * One row per HR-requested device snapshot. Lifecycle is created
 * PENDING alongside a SNAPSHOT_DEVICE sync task; the poller drains
 * the task, dials the device, then POSTs the dump back to
 * {@code POST /devices/{id}/snapshots/{snapshotId}/finalize} which
 * fills the jsonb columns and flips status to COMPLETED. On poller
 * failure the row goes to FAILED with the error message.
 */
@Entity
@Table(name = "attendance_device_snapshots")
@Getter
@Setter
public class DeviceSnapshot extends AuditedEntity {

    @Column(name = "device_id", nullable = false)
    private Long deviceId;

    @Column(name = "sync_task_id")
    private Long syncTaskId;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 16)
    private DeviceSnapshotStatus status = DeviceSnapshotStatus.PENDING;

    @Column(name = "requested_by", nullable = false, length = 64)
    private String requestedBy;

    @Column(name = "completed_at")
    private LocalDateTime completedAt;

    @Column(name = "device_info", columnDefinition = "jsonb")
    @ColumnTransformer(write = "?::jsonb")
    private String deviceInfo;

    @Column(name = "users", columnDefinition = "jsonb")
    @ColumnTransformer(write = "?::jsonb")
    private String users;

    @Column(name = "attendance", columnDefinition = "jsonb")
    @ColumnTransformer(write = "?::jsonb")
    private String attendance;

    @Column(name = "user_count")
    private Integer userCount;

    @Column(name = "record_count")
    private Integer recordCount;

    @Column(name = "error")
    private String error;
}
