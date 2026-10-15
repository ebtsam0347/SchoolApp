package com.forward.hrms.attendance.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(name = "OvertimeMineRow",
        description = "OT request enriched with the attendance-record minutes for the same " +
                "(employee, workDate). Minutes are zero when the OT is still PENDING/REJECTED " +
                "or when no attendance record exists for the day yet.")
public record OvertimeMineRow(
        @Schema(description = "Underlying OT request — authorization shape, no minutes.")
        OvertimeRequestDto request,
        int otNormalMinutes,
        int otExtraMinutes,
        int otSpecialMinutes,
        int workedMinutes
) {}
