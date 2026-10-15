package com.forward.hrms.attendance.entity;

import com.forward.hrms.commons.core.jpa.AuditedEntity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import jakarta.persistence.Transient;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDate;
import java.time.LocalTime;

/**
 * Operator-driven shift override over a date range. Wins over the
 * assigned shift in both punch resolution and snapshot stamping.
 * Overlap with another override for the same employee is rejected at
 * the service layer (no DB-level exclude constraint).
 */
@Entity
@Table(name = "attendance_shift_overrides")
@Getter
@Setter
public class AttendanceShiftOverride extends AuditedEntity {

    @Column(name = "employee_id", nullable = false)
    private Long employeeId;

    @Column(name = "from_date", nullable = false)
    private LocalDate fromDate;

    @Column(name = "to_date", nullable = false)
    private LocalDate toDate;

    @Column(name = "shift_start_time", nullable = false)
    private LocalTime shiftStartTime;

    @Column(name = "shift_end_time", nullable = false)
    private LocalTime shiftEndTime;

    @Column(name = "shift_break_start_time", nullable = false)
    private LocalTime shiftBreakStartTime;

    @Column(name = "shift_break_end_time", nullable = false)
    private LocalTime shiftBreakEndTime;

    @Column(name = "shift_grace_minutes", nullable = false)
    private int shiftGraceMinutes;

    @Column(name = "reason")
    private String reason;

    /** Derived from the override break window. */
    @Transient
    public int getShiftBreakMinutes() {
        return Shift.computeBreakMinutes(shiftBreakStartTime, shiftBreakEndTime);
    }
}
