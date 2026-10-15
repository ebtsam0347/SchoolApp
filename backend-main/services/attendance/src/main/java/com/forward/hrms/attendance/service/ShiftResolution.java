package com.forward.hrms.attendance.service;

import com.forward.hrms.attendance.entity.Shift;

import java.time.LocalTime;

/**
 * Resolved effective shift for a (employee, date) pair. {@code shiftId}
 * is null for override-only days (no underlying assignment) — the
 * snapshot still gets stamped onto the {@code attendance_records} row.
 * Break is a window; {@link #breakMinutes()} is the derived duration.
 */
public record ShiftResolution(
        Long shiftId,
        LocalTime startTime,
        LocalTime endTime,
        LocalTime breakStartTime,
        LocalTime breakEndTime,
        int graceMinutes
) {

    public int breakMinutes() {
        return Shift.computeBreakMinutes(breakStartTime, breakEndTime);
    }

    public boolean crossesMidnight() {
        return !endTime.isAfter(startTime);
    }
}
