package com.forward.hrms.attendance.dto.response;

import com.forward.hrms.employee.dto.response.EmployeeSummary;

import io.swagger.v3.oas.annotations.media.Schema;

import java.util.List;

@Schema(name = "AttendanceRecordsPage",
        description = "Org-wide attendance records list, page-by-page. Each row carries the upstream " +
                "record plus a compact `employee` summary (name, code, department, photo) so the FE " +
                "can render the table without an extra fan-out per row. Pagination metadata mirrors " +
                "the upstream PageResponse 1:1.")
public record AttendanceRecordsPage(
        List<Row> content,
        int page,
        int size,
        long totalElements,
        int totalPages,
        @Schema(nullable = true) String sort
) {
    @Schema(name = "AttendanceRecordsPage.Row")
    public record Row(AttendanceRecordDto record,
                      @Schema(nullable = true) EmployeeSummary employee) {}
}
