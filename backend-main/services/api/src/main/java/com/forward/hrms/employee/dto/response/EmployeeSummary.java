package com.forward.hrms.employee.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * Compact employee row used by every BFF / service composite that
 * needs to surface a name + code + the three primary org labels for
 * a list-of-ids style read. Resolved server-side from the shared
 * employee-display cache; see commons-cache's {@code EmployeeDisplayCache}
 * and {@code EmployeeSummaryMapper}.
 *
 * <p>Pure DTO contract — kept free of static factories that depend on
 * commons-cache, so the api module stays a transport-only contract.
 * Conversion helpers live in {@code commons-cache.EmployeeSummaryMapper}.
 */
@Schema(name = "EmployeeSummary",
        description = "Compact employee row for resolving names against an id list. Used by the " +
                "approvals inbox, payroll composites, attendance composites, and any surface that " +
                "comes back keyed by employee id without names attached.")
public record EmployeeSummary(
        @Schema(description = "Employee id.") long id,
        @Schema(description = "Display name.") String fullName,
        @Schema(description = "Human-readable employee code (e.g. FS-2026-0042). " +
                "Also the RFID/punch-card number used by attendance devices.")
        String employeeCode,
        @Schema(nullable = true, description = "Department id.")
        Long departmentId,
        @Schema(nullable = true, description = "Department name, looked up from the catalog.")
        String departmentName,
        @Schema(nullable = true, description = "Section id.")
        Long sectionId,
        @Schema(nullable = true, description = "Section name, looked up from the catalog.")
        String sectionName,
        @Schema(nullable = true, description = "Designation id.")
        Long designationId,
        @Schema(nullable = true, description = "Designation title, looked up from the catalog.")
        String designationName,
        @Schema(nullable = true, description = "Signed URL for the employee's profile photo.")
        String profilePictureUrl
) {

    /** Identity-only fallback when an employee id resolves to nothing (deleted / never existed). */
    public static EmployeeSummary placeholder(long id, String employeeCode) {
        return new EmployeeSummary(id, "Employee " + id, employeeCode,
                null, null, null, null, null, null, null);
    }
}
