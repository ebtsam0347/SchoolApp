package com.forward.hrms.commons.approval.spi;

/** Resolves the {@code DEPARTMENT_HEAD} stage kind — a fixed target department, not the submitter's own. */
@FunctionalInterface
public interface DepartmentHeadResolver {

    /**
     * @param departmentCode the stage's configured target department code
     * @return that department head's user id, or {@code null} if unresolved
     */
    Long resolveDepartmentHeadUserId(String departmentCode);
}
