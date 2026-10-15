package com.forward.hrms.attendance.approval;

import com.forward.hrms.attendance.client.EmployeeClient;
import com.forward.hrms.attendance.service.CallerContext;
import com.forward.hrms.commons.approval.spi.ManagerResolver;

import lombok.RequiredArgsConstructor;

import org.springframework.stereotype.Component;

/**
 * Resolves the manager user id for a MANAGER-kind approval stage.
 *
 * <p>For the MANAGER kind we usually want the submitter's own manager —
 * the same row {@link CallerContext} already cached on the request, so the
 * common self-submit path costs no upstream call. Only when the chain is
 * opened on behalf of a different employee — on-behalf leave / OT /
 * regularization, bulk OT — do we fall back to
 * {@link EmployeeClient#getById}, which routes through the
 * authenticated-only {@code /employees/by-ids} rather than the
 * {@code employee.read}-gated {@code GET /employees/{id}}.
 *
 * <p>Returns the manager's <em>user id</em>, not their employee id —
 * {@code approval_steps.assigned_to} is keyed on user_id by every
 * read path (inbox query, canAct, delegation join, notification dispatch).
 */
@Component
@RequiredArgsConstructor
public class AttendanceManagerResolver implements ManagerResolver {

    private final EmployeeClient employees;
    private final CallerContext caller;

    @Override
    public Long resolveManagerUserId(long employeeId) {
        EmployeeClient.EmployeeView me = caller.currentEmployee().orElse(null);
        if (me != null && me.id() == employeeId) {
            return me.managerUserId();
        }
        return employees.getById(employeeId).managerUserId();
    }
}
