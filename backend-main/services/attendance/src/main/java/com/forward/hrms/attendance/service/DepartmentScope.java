package com.forward.hrms.attendance.service;

import com.forward.hrms.attendance.client.EmployeeClient;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * The employee ids a team-request generator may list requests for: everyone
 * in their own department, themselves included. Resolved per call from
 * employee-service's permission-free {@code /employees/me/department/employees},
 * so the caller never needs {@code employee.read}.
 *
 * <p>Backs the {@code …/department} lists on the "Generated team requests"
 * page — the TRG counterpart of a manager seeing their direct reports'
 * requests.
 */
@Component
@RequiredArgsConstructor
public class DepartmentScope {

    private final EmployeeClient employees;

    /**
     * The caller's department, optionally narrowed to one {@code employeeId}.
     * Empty when the caller has no department, or when {@code employeeId}
     * isn't in it — the lists then answer an empty page rather than a 403, so
     * the id can't be probed.
     */
    public List<Long> employeeIds(Long employeeId) {
        List<Long> ids = employees.myDepartmentEmployeeIds();
        if (employeeId == null) return ids;
        return ids.contains(employeeId) ? List.of(employeeId) : List.of();
    }
}
