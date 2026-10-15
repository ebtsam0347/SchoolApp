package com.forward.hrms.attendance.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;

import java.time.LocalDateTime;
import java.util.List;

@Schema(name = "BiometricBatchRequest",
        description = "Bulk ingest from attendance-poller. Each entry is a raw punch — the service " +
                "resolves the employee from employeeCode (the device's user_id field IS the " +
                "employee_code) via attendance_employees, drops same-time duplicates silently, and " +
                "drives attendance_records re-derivation transactionally. Entries whose " +
                "employee_code has no matching snapshot row are returned in skipped[] with reason " +
                "UNKNOWN_DEVICE_USER and parked in the unmatched-replay queue.")
public record BiometricBatchRequest(
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) @NotEmpty List<Punch> punches
) {

    @Schema(name = "BiometricPunch")
    public record Punch(
            @Schema(requiredMode = Schema.RequiredMode.REQUIRED,
                    description = "Device-side user_id, equal to employees.employee_code.")
            @NotBlank String employeeCode,
            @Schema(requiredMode = Schema.RequiredMode.REQUIRED) @NotNull LocalDateTime punchedAt,
            @Schema(requiredMode = Schema.RequiredMode.REQUIRED, description = "FK into attendance_devices.id")
            @NotNull Long deviceId
    ) {}
}
