package com.forward.hrms.commons.cache;

import com.forward.hrms.employee.dto.response.DepartmentDto;
import com.forward.hrms.employee.dto.response.DesignationDto;
import com.forward.hrms.employee.dto.response.EmployeeDto;
import com.forward.hrms.employee.dto.response.EmployeeSummary;
import com.forward.hrms.employee.dto.response.SectionDto;

/**
 * Converters from cache shape ({@link EmployeeDisplay}) and from raw
 * upstream rows ({@link EmployeeDto} + {@link CatalogCache.CatalogIndex})
 * into the shared {@link EmployeeSummary} wire DTO.
 *
 * <p>Lives in commons-cache (not in the api module) so the {@code api}
 * module stays a pure transport contract with no dep on Redis / cache
 * machinery. Every consumer that builds an {@code EmployeeSummary} from
 * cache or catalog data should go through here so the mapping logic is
 * one place.
 */
public final class EmployeeSummaryMapper {

    private EmployeeSummaryMapper() {}

    /** From the cache shape — the common path. */
    public static EmployeeSummary toSummary(EmployeeDisplay d) {
        if (d == null) return null;
        return new EmployeeSummary(
                d.employeeId(), d.fullName(), d.employeeCode(),
                d.departmentId(), d.departmentName(),
                d.sectionId(),    d.sectionName(),
                d.designationId(), d.designationTitle(),
                d.profilePictureUrl());
    }

    /**
     * For callers that already hold an {@link EmployeeDto} (e.g. picker
     * results from a search) and just need to attach catalog labels —
     * skips the cache round-trip for the employee row.
     */
    public static EmployeeSummary toSummary(EmployeeDto e, CatalogCache.CatalogIndex idx) {
        if (e == null) return null;
        DepartmentDto d = e.departmentId() == null ? null : idx.departments().get(e.departmentId());
        SectionDto s = e.sectionId() == null ? null : idx.sections().get(e.sectionId());
        DesignationDto g = e.designationId() == null ? null : idx.designations().get(e.designationId());
        return new EmployeeSummary(
                e.id(), e.fullName(), e.employeeCode(),
                e.departmentId(), d == null ? null : d.name(),
                e.sectionId(),    s == null ? null : s.name(),
                e.designationId(), g == null ? null : g.title(),
                e.profilePictureUrl());
    }
}
