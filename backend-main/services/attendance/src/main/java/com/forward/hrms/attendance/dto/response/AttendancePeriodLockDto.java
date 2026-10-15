package com.forward.hrms.attendance.dto.response;

import com.forward.hrms.attendance.entity.AttendancePeriodLock;

import io.swagger.v3.oas.annotations.media.Schema;

import java.time.LocalDateTime;

@Schema(name = "AttendancePeriodLock", description = "Lock state for a payroll-period (year + month) in attendance.")
public record AttendancePeriodLockDto(
        @Schema(example = "2026") int periodYear,
        @Schema(example = "4")    int periodMonth,
        @Schema(example = "2026-05-09T16:48:00") LocalDateTime lockedAt,
        @Schema(example = "158")  String lockedBy
) {
    public static AttendancePeriodLockDto of(AttendancePeriodLock l) {
        return new AttendancePeriodLockDto(l.getPeriodYear(), l.getPeriodMonth(), l.getLockedAt(), l.getLockedBy());
    }
}
