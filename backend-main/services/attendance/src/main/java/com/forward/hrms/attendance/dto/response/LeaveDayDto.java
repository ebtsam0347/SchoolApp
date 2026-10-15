package com.forward.hrms.attendance.dto.response;

import com.forward.hrms.attendance.entity.LeaveRequestDay;

import io.swagger.v3.oas.annotations.media.Schema;

import java.time.LocalDateTime;
import java.time.LocalDate;

@Schema(name = "LeaveDayDto")
public record LeaveDayDto(
        long id,
        LocalDate workDate,
        String status,
        @Schema(nullable = true) Long decidedBy,
        @Schema(nullable = true) LocalDateTime decidedAt,
        @Schema(nullable = true) Integer decidedAtStage,
        @Schema(nullable = true) String rejectionReason
) {
    public static LeaveDayDto of(LeaveRequestDay d) {
        return new LeaveDayDto(d.getId(), d.getAttendanceDate(), d.getStatus().name(),
                d.getDecidedBy(), d.getDecidedAt(), d.getDecidedAtStage(), d.getRejectionReason());
    }
}
