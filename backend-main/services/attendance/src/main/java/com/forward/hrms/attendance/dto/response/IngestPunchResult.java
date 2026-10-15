package com.forward.hrms.attendance.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;

import java.time.LocalDateTime;
import java.util.List;

@Schema(name = "IngestPunchResult",
        description = "Outcome of a /punches/ingest call. Punches whose employee_code has no " +
                "matching attendance_employees snapshot row land in skipped[] with reason " +
                "UNKNOWN_DEVICE_USER; the poller logs them and they are parked in the " +
                "unmatched-replay queue.")
public record IngestPunchResult(
        List<PunchDto> ingested,
        List<Skipped> skipped
) {
    @Schema(name = "SkippedPunch")
    public record Skipped(
            String employeeCode,
            long deviceId,
            LocalDateTime punchedAt,
            String reason
    ) {}
}
