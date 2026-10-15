package com.forward.hrms.attendance.entity;

import com.forward.hrms.attendance.enums.AttendanceStatus;
import com.forward.hrms.attendance.enums.PunchSource;
import com.forward.hrms.commons.core.jpa.AuditedEntity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import jakarta.persistence.Transient;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;
import java.time.LocalDate;
import java.time.LocalTime;

/**
 * Per-day projection over {@link AttendancePunch}. Recomputed on every
 * new punch. Shift fields are a snapshot — they do NOT change when the
 * shift catalog is edited or the assignment changes; the only
 * post-creation rewriter is an operator shift override.
 */
@Entity
@Table(name = "attendance_records")
@Getter
@Setter
public class AttendanceRecord extends AuditedEntity {

    @Column(name = "employee_id", nullable = false)
    private Long employeeId;

    @Column(name = "attendance_date", nullable = false)
    private LocalDate attendanceDate;

    @Column(name = "check_in")
    private LocalDateTime checkIn;

    @Column(name = "check_out")
    private LocalDateTime checkOut;

    @Column(name = "effective_shift")
    private Long effectiveShift;

    @Column(name = "shift_start_time")
    private LocalTime shiftStartTime;

    @Column(name = "shift_end_time")
    private LocalTime shiftEndTime;

    /**
     * {@code attendanceDate + shiftStartTime}. Derived in
     * {@link #deriveShiftDateTimes()} — no setter, so it can't drift from
     * the time columns.
     */
    @Setter(AccessLevel.NONE)
    @Column(name = "shift_start_date_time")
    private LocalDateTime shiftStartDateTime;

    /** {@code attendanceDate + shiftEndTime}, next day when the shift crosses midnight. */
    @Setter(AccessLevel.NONE)
    @Column(name = "shift_end_date_time")
    private LocalDateTime shiftEndDateTime;

    @Column(name = "shift_break_start_time")
    private LocalTime shiftBreakStartTime;

    @Column(name = "shift_break_end_time")
    private LocalTime shiftBreakEndTime;

    @Column(name = "shift_grace_minutes")
    private Integer shiftGraceMinutes;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 16)
    private AttendanceStatus status;

    @Column(name = "late_minutes")
    private int lateMinutes;

    @Column(name = "early_minutes")
    private int earlyMinutes;

    @Column(name = "worked_minutes")
    private int workedMinutes;

    @Column(name = "ot_normal_minutes")
    private int otNormalMinutes;

    @Column(name = "ot_extra_minutes")
    private int otExtraMinutes;

    @Column(name = "ot_special_minutes")
    private int otSpecialMinutes;

    @Enumerated(EnumType.STRING)
    @Column(name = "source", length = 16)
    private PunchSource source;

    @Column(name = "notes")
    private String notes;

    /**
     * §7.payroll #23 — paid-leave flag. Populated when {@code status ==
     * LEAVE} from the leave type's {@code paid} column at approval
     * time. Null on every other status. Payroll uses it to decide
     * whether a LEAVE day counts toward paid days or LOP.
     */
    @Column(name = "paid")
    private Boolean paid;

    /**
     * Keeps the shift date-times in step with the shift time columns on
     * every insert / update, whichever path wrote the times (nightly
     * create, recompute, shift override). Midnight rule matches
     * ShiftResolution#crossesMidnight: end <= start ⇒ next day.
     */
    @PrePersist
    @PreUpdate
    void deriveShiftDateTimes() {
        if (attendanceDate == null || shiftStartTime == null || shiftEndTime == null) {
            shiftStartDateTime = null;
            shiftEndDateTime = null;
            return;
        }
        shiftStartDateTime = attendanceDate.atTime(shiftStartTime);
        LocalDate endDate = shiftEndTime.isAfter(shiftStartTime) ? attendanceDate : attendanceDate.plusDays(1);
        shiftEndDateTime = endDate.atTime(shiftEndTime);
    }

    /** Derived from the snapshot break window. Returns 0 when either bound is null. */
    @Transient
    public int getShiftBreakMinutes() {
        return Shift.computeBreakMinutes(shiftBreakStartTime, shiftBreakEndTime);
    }
}
