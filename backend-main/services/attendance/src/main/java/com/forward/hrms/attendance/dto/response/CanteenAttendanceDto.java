package com.forward.hrms.attendance.dto.response;

import com.forward.hrms.employee.dto.response.EmployeeSummary;

import io.swagger.v3.oas.annotations.media.Schema;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;

@Schema(name = "CanteenAttendanceDto",
        description = "One canteen meal register entry. `employee` is the shared EmployeeSummary " +
                "(code, name, department, section, designation, grade) resolved from the " +
                "employee-display cache at read time — nothing but the id is stored on the row.")
public record CanteenAttendanceDto(
        long id,
        @Schema(description = "Employee-schema employee id.") long employeeId,
        @Schema(nullable = true, description = "Resolved employee row; null only if the id no longer resolves.")
        EmployeeSummary employee,
        LocalDate attendanceDate,
        LocalTime attendanceTime,
        @Schema(nullable = true, description = "IP of the canteen device. Null on manual entries; " +
                "populated once the canteen device poller ships.")
        String deviceIp,
        @Schema(description = "Meals this entry covers.", example = "1") int count,
        LocalDateTime createdAt,
        String createdBy,
        LocalDateTime updatedAt,
        String updatedBy
) {}
