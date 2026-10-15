package com.forward.hrms.payroll.approval;

import com.forward.hrms.commons.approval.spi.ManagerResolver;
import com.forward.hrms.payroll.client.EmployeeClient;

import lombok.RequiredArgsConstructor;

import org.springframework.stereotype.Component;

/**
 * Resolves the MANAGER-kind stage on both advance chains via employee-service.
 *
 * <p>Mirrors {@code AttendanceManagerResolver} (leave/OT/regularization):
 * when the chain is opening against the caller's own employee id — the
 * common case for a self-raised short-term advance batch — resolve via the
 * self-serve {@code GET /employees/me} instead of {@code GET /employees/{id}}.
 * The latter needs {@code employee.read}, which a plain {@code payroll.write}
 * holder doesn't have; the call 403s, is swallowed, and the stage opens with
 * a null assignee that no inbox query can ever surface. Falls back to the
 * permission-gated lookup only when opening on behalf of a different
 * employee (e.g. HR raising a permanent advance for someone else).
 */
@Component
@RequiredArgsConstructor
public class PayrollManagerResolver implements ManagerResolver {

    private final EmployeeClient employeeClient;

    @Override
    public Long resolveManagerUserId(long employeeId) {
        EmployeeClient.Me me = employeeClient.me();
        if (me.employeeId() != null && me.employeeId() == employeeId) {
            return me.managerUserId();
        }
        return employeeClient.managerUserId(employeeId);
    }
}
