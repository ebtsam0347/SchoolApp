package com.forward.hrms.attendance.entity;

import com.forward.hrms.attendance.enums.ShiftPeriod;
import com.forward.hrms.commons.core.jpa.AuditedEntity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import jakarta.persistence.Transient;
import lombok.Getter;
import lombok.Setter;

import java.time.Duration;
import java.time.LocalTime;

/**
 * Shift catalog row. Sunday is the system-wide weekly off (no per-shift
 * override). When {@code endTime <= startTime} the shift crosses
 * midnight; the flag is derived in code, not stored. Friday-only
 * deviations live on the {@code fridayExtraMinutes} override —
 * applied to break + end uniformly. Break is a window
 * ({@code breakStartTime} → {@code breakEndTime}); duration is
 * derived via {@link #getBreakMinutes()}.
 */
@Entity
@Table(name = "shifts")
@Getter
@Setter
public class Shift extends AuditedEntity {

    @Column(name = "code", nullable = false, length = 32, unique = true)
    private String code;

    @Column(name = "name", nullable = false, length = 64)
    private String name;

    @Column(name = "start_time", nullable = false)
    private LocalTime startTime;

    @Column(name = "end_time", nullable = false)
    private LocalTime endTime;

    @Column(name = "break_start_time")
    private LocalTime breakStartTime;

    @Column(name = "break_end_time")
    private LocalTime breakEndTime;

    @Column(name = "grace_minutes", nullable = false)
    private int graceMinutes = 10;

    @Column(name = "friday_extra_minutes")
    private Integer fridayExtraMinutes;

    @Column(name = "active", nullable = false)
    private boolean active = true;

    @Column(name = "display_order", nullable = false)
    private int displayOrder = 0;

    /** Day or night shift. Operator dashboard filters on it. */
    @Enumerated(EnumType.STRING)
    @Column(name = "period", nullable = false, length = 8)
    private ShiftPeriod period = ShiftPeriod.DAY;

    /** Derived from the break window. Returns 0 when either bound is null. */
    @Transient
    public int getBreakMinutes() {
        return computeBreakMinutes(breakStartTime, breakEndTime);
    }

    public static int computeBreakMinutes(LocalTime start, LocalTime end) {
        if (start == null || end == null) return 0;
        long minutes = Duration.between(start, end).toMinutes();
        if (minutes < 0) minutes += 24 * 60; // crosses midnight
        return Math.toIntExact(minutes);
    }
}
