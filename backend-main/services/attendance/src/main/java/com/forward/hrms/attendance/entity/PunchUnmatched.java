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
 * Queue of biometric punches whose {@code (device_id, employee_code)}
 * had no matching row in {@link AttendanceEmployee} at ingest time.
 * The scheduled replay job in {@code DevicePunchReplayJob} drains it
 * whenever a snapshot row catches up; rows past
 * {@code hrms.attendance.poller.unmatched-max-attempts} (default 8) are
 * dropped + audit-logged.
 */
@Entity
@Table(name = "attendance_punch_unmatched",
        uniqueConstraints = @UniqueConstraint(
                name = "uk_punch_unmatched_device_employee_code_at",
                columnNames = {"device_id", "employee_code", "punched_at"}))
@Getter
@Setter
public class PunchUnmatched extends AuditedEntity {

    @Column(name = "device_id", nullable = false)
    private Long deviceId;

    @Column(name = "employee_code", nullable = false, length = 32)
    private String employeeCode;

    @Column(name = "punched_at", nullable = false)
    private LocalDateTime punchedAt;

    @Column(name = "attempts", nullable = false)
    private int attempts = 0;

    @Column(name = "first_seen_at", nullable = false, updatable = false)
    private LocalDateTime firstSeenAt;

    @Column(name = "last_attempt_at")
    private LocalDateTime lastAttemptAt;
}
