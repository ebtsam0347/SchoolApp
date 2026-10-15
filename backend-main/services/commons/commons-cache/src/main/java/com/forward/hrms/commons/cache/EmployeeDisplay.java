package com.forward.hrms.commons.cache;

/**
 * Resolved snapshot of an employee's display fields. The cached value
 * behind {@code hrms:cache:emp:{id}}. All fields nullable except
 * {@code employeeId} and {@code employeeCode} (the stable identity);
 * everything else may be absent if the catalog row was missing or the
 * employee row didn't have it set.
 *
 * <p>Catalog names are resolved at write time (when the
 * {@code employee.events} consumer fans out the cache update), not at
 * read time — the cached value is fully self-contained.
 */
public record EmployeeDisplay(
        long employeeId,
        String employeeCode,
        String fullName,
        Long departmentId,
        String departmentName,
        Long sectionId,
        String sectionName,
        Long designationId,
        String designationTitle,
        String profilePictureUrl
) {

    /** Identity-only fallback when an employee id resolves to nothing (deleted / never existed). */
    public static EmployeeDisplay placeholder(long id) {
        return new EmployeeDisplay(id, null, "Employee " + id,
                null, null, null, null, null, null, null);
    }
}
