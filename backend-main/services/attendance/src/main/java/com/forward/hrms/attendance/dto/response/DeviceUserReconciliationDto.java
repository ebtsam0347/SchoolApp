package com.forward.hrms.attendance.dto.response;

import com.forward.hrms.attendance.enums.DeviceUserMatchCategory;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(name = "DeviceUserReconciliationDto",
        description = "One device-enrolled user reconciled against the employee mirror. The device " +
                "user_id (leading zeros stripped) is matched to an employee_code; category tells " +
                "HR whether to keep or un-enroll.")
public record DeviceUserReconciliationDto(
        @Schema(description = "Raw user_id as stored on the device (before zero-strip). Pass this " +
                "back verbatim to the un-enroll endpoint for UNMATCHED rows.")
        String deviceUserId,
        @Schema(nullable = true, description = "employee_code matched (device user_id, zeros " +
                "stripped); null when the device user_id is blank.")
        String employeeCode,
        @Schema(nullable = true, description = "Matched employee id; null when UNMATCHED.")
        Long employeeId,
        @Schema(nullable = true, description = "Employee full name when matched; null otherwise.")
        String name,
        @Schema(nullable = true, description = "Employment status from the mirror when matched " +
                "(ACTIVE / TERMINATED).")
        String employmentStatus,
        DeviceUserMatchCategory category,
        short privilege
) {}
