package com.forward.hrms.attendance.dto.response;

import com.forward.hrms.employee.dto.response.EmployeeSummary;

import io.swagger.v3.oas.annotations.media.Schema;

import java.util.List;

@Schema(name = "AttendancePunchesPage",
        description = "Org-wide punches list, page-by-page. Same contract as AttendanceRecordsPage — " +
                "raw punches enriched with a compact `employee` summary. Primary table for the " +
                "attendance-operator's daily reconciliation.")
public record AttendancePunchesPage(
        List<Row> content,
        int page,
        int size,
        long totalElements,
        int totalPages,
        @Schema(nullable = true) String sort
) {
    @Schema(name = "AttendancePunchesPage.Row")
    public record Row(PunchDto punch,
                      @Schema(nullable = true) EmployeeSummary employee) {}
}
