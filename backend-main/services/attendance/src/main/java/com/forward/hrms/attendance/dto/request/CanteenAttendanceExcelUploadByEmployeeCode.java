package com.forward.hrms.attendance.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.HashMap;

import jakarta.validation.constraints.NotNull;

public record CanteenAttendanceExcelUploadByEmployeeCode(
        @Schema(description = "Employee codes of the employees. employeeCode:count",
                example = "{\"4\": 1, \"7\": 2, \"16\": 5}")
        @NotNull
        HashMap<String, Integer> excelUploadByEmployeeCode,

        @Schema(description = "Date of attendance, in server-local time.", example = "2024-06-01")
        @NotNull
        LocalDate attendanceDate,

        @Schema(description = "Time of attendance, in server-local time.", example = "13:30:00")
        @NotNull
        LocalTime attendanceTime

) {
}
