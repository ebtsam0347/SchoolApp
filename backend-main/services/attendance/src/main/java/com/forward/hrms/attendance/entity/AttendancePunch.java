package com.forward.hrms.attendance.entity;

import com.forward.hrms.attendance.enums.PunchDirection;
import com.forward.hrms.attendance.enums.PunchSource;
import com.forward.hrms.commons.core.jpa.AuditedEntity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;
import java.time.LocalDate;

/**
 * Raw punch log. Source of truth for {@link AttendanceRecord} —
 * check_in / check_out are derived (min / max punched_at per
 * (employee, attendance_date)), unless a punch carries a {@link #direction}.
 */
@Entity
@Table(name = "attendance_punches")
@Getter
@Setter
public class AttendancePunch extends AuditedEntity {

    @Column(name = "employee_id", nullable = false)
    private Long employeeId;

    @Column(name = "punched_at", nullable = false)
    private LocalDateTime punchedAt;

    @Column(name = "attendance_date", nullable = false)
    private LocalDate attendanceDate;

    @Enumerated(EnumType.STRING)
    @Column(name = "source", nullable = false, length = 16)
    private PunchSource source;

    @Column(name = "device_id")
    private Long deviceId;

    @Column(name = "reason")
    private String reason;

    /**
     * Set only on operator punches added against a record: IN makes this the
     * day's check-in, OUT its check-out, whatever other punches exist. Null =
     * derived by position (earliest / latest).
     */
    @Enumerated(EnumType.STRING)
    @Column(name = "direction", length = 8)
    private PunchDirection direction;
}
