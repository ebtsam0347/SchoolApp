package com.forward.hrms.attendance.entity;

import com.forward.hrms.attendance.enums.DeviceType;
import com.forward.hrms.commons.core.jpa.AuditedEntity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

/**
 * Punch from a non-attendance device (canteen, perimeter gate). These
 * rows never feed {@link AttendanceRecord} — they bypass shift
 * resolution, the recompute, and the {@code punch_recorded} outbox
 * event. {@code employee_id} is resolved from {@code employee_code} at
 * ingest when the snapshot knows the code; null otherwise. The
 * {@code (device_id, employee_code, punched_at)} unique constraint
 * makes biometric retransmits idempotent.
 */
@Entity
@Table(name = "device_event_logs")
@Getter
@Setter
public class DeviceEventLog extends AuditedEntity {

    @Column(name = "device_id", nullable = false)
    private Long deviceId;

    @Column(name = "employee_code", nullable = false, length = 64)
    private String employeeCode;

    @Column(name = "employee_id")
    private Long employeeId;

    @Column(name = "punched_at", nullable = false)
    private LocalDateTime punchedAt;

    @Enumerated(EnumType.STRING)
    @Column(name = "device_type", nullable = false, length = 16)
    private DeviceType deviceType;
}
