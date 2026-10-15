package com.forward.hrms.employee.service;

import com.forward.hrms.employee.repository.DepartmentRepository;
import com.forward.hrms.employee.repository.EmployeeRepository;

import lombok.RequiredArgsConstructor;

import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * Answers a single org-structure question: is an employee a Manager or a
 * department Head (HOD)? Derived purely from existing data — direct reports
 * ({@code employees.manager_id}) and department headship
 * ({@code departments.head_id}) — with no dedicated flag, role or grade.
 *
 * <p>Small focused helper in the same spirit as {@link ManagerCycleChecker};
 * promoted to a {@code commons-*} module only once a third caller appears.
 */
@Component
@RequiredArgsConstructor
public class ManagerHodChecker {

    private final EmployeeRepository employees;
    private final DepartmentRepository departments;

    /**
     * True when {@code employeeId} line-manages at least one active employee
     * or is recorded as the head of a department.
     */
    @Transactional(propagation = Propagation.MANDATORY)
    public boolean isManagerOrHod(long employeeId) {
        return employees.hasActiveDirectReports(employeeId)
                || departments.existsByHeadId(employeeId);
    }
}
