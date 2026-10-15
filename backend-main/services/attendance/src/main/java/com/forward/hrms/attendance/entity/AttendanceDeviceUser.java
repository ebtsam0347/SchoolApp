package com.forward.hrms.attendance.entity;

import com.forward.hrms.commons.core.jpa.AuditedEntity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

/**
 * Source-of-truth row for "this employee should exist on this device."
 * The device-side user_id is the employee's {@code employee_code}
 * (resolved through {@link AttendanceEmployee} at sync time); there
 * is no per-device card field on the bridge — that mismatch was the
 * old design and is gone. {@code hasFingerprint} / {@code hasFace}
 * track what's currently provisioned on the device side, so a
 * PUSH_TEMPLATE task can skip devices that already carry it.
 * {@code syncedAt} flips on the most recent successful UPSERT_USER
 * completion.
 */
@Entity
@Table(name = "attendance_device_users",
        uniqueConstraints = @UniqueConstraint(
                name = "uk_device_users_device_employee",
                columnNames = {"device_id", "employee_id"}))
@Getter
@Setter
public class AttendanceDeviceUser extends AuditedEntity {

    @Column(name = "device_id", nullable = false)
    private Long deviceId;

    @Column(name = "employee_id", nullable = false)
    private Long employeeId;

    @Column(name = "privilege", nullable = false)
    private short privilege = 0;

    @Column(name = "has_fingerprint", nullable = false)
    private boolean hasFingerprint = false;

    @Column(name = "has_face", nullable = false)
    private boolean hasFace = false;

    @Column(name = "synced_at")
    private LocalDateTime syncedAt;
}
