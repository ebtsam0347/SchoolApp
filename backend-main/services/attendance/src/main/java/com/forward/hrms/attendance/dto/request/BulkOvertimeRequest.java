package com.forward.hrms.attendance.dto.request;

import com.forward.hrms.attendance.enums.OvertimeType;

import io.swagger.v3.oas.annotations.media.Schema;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;

import java.time.LocalDate;
import java.util.List;

@Schema(name = "BulkOvertimeRequest",
        description = "Bulk OT submission. All employees must be in the submitter's department; " +
                "rows for employees with no punch on the date are skipped (returned in `skipped[]`). " +
                "`overtimeType` and `factoryCode` apply to every row in the batch.")
public record BulkOvertimeRequest(
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) @NotNull LocalDate workDate,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED,
                description = "Which OT bucket the day's overtime is credited to, for every employee in the batch.")
        @NotNull OvertimeType overtimeType,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED,
                allowableValues = {"B34002", "B34003", "B34004", "B34005", "B34006", "B34007", "Administrator"},
                description = "Factory / unit the overtime is worked for, for every employee in the batch.")
        @NotNull com.forward.hrms.attendance.enums.FactoryCode factoryCode,
        @Schema(nullable = true) String reason,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) @NotEmpty List<Long> employeeIds
) {}
