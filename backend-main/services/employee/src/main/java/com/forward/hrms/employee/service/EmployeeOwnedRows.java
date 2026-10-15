package com.forward.hrms.employee.service;

import java.util.function.Function;

import org.springframework.data.jpa.repository.JpaRepository;

import com.forward.hrms.commons.core.error.NotFoundError;
import com.forward.hrms.employee.enums.EmployeeErrorCode;
import com.forward.hrms.employee.repository.EmployeeRepository;


public final class EmployeeOwnedRows {

    private EmployeeOwnedRows() {}

    /** Throws NotFoundError(EMPLOYEE_NOT_FOUND) when the employee row is absent. */
    public static void ensureEmployee(EmployeeRepository employees, long employeeId) {
        if (!employees.existsById(employeeId)) {
            throw new NotFoundError(EmployeeErrorCode.EMPLOYEE_NOT_FOUND, "employee not found");
        }
    }

    /**
     * Loads {@code id} from {@code repo}; throws NotFoundError({@code notFoundCode})
     * either when the row is missing or when its employee_id doesn't match
     * {@code employeeId}. {@code employeeIdOf} extracts the row's employee
     * pointer (typically a method reference like {@code EmployeeSkill::getEmployeeId}).
     */
    public static <T> T requireOwned(JpaRepository<T, Long> repo,
                                     Function<T, Long> employeeIdOf,
                                     long employeeId,
                                     long id,
                                     EmployeeErrorCode notFoundCode,
                                     String label) {
        T row = repo.findById(id)
                .orElseThrow(() -> new NotFoundError(notFoundCode, label + " not found"));
        if (!employeeIdOf.apply(row).equals(employeeId)) {
            throw new NotFoundError(notFoundCode, label + " does not belong to this employee");
        }
        return row;
    }
}
