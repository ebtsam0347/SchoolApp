package com.forward.hrms.attendance.dto.response;

import com.forward.hrms.attendance.entity.EmployeeShift;

import io.swagger.v3.oas.annotations.media.Schema;

import java.time.LocalDate;

@Schema(name = "EmployeeShiftDto",
        description = "An employee's shift assignment over a time range. `effectiveTo` is null for " +
                "the currently-open assignment. Returned newest-first by `effectiveFrom`; the first " +
                "row whose range covers today is the active one.")
public record EmployeeShiftDto(
        long id,
        long employeeId,
        long shiftId,
        @Schema(description = "Denormalized shift code at read time.")
        String shiftCode,
        @Schema(description = "Denormalized shift name at read time.")
        String shiftName,
        LocalDate effectiveFrom,
        @Schema(nullable = true) LocalDate effectiveTo
) {
    public static EmployeeShiftDto of(EmployeeShift es, String shiftCode, String shiftName) {
        return new EmployeeShiftDto(es.getId(), es.getEmployeeId(), es.getShiftId(),
                shiftCode, shiftName,
                es.getEffectiveFrom(), es.getEffectiveTo());
    }
}
