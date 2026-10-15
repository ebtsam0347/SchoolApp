package com.forward.hrms.attendance.service;

import com.forward.hrms.attendance.client.EmployeeClient;
import com.forward.hrms.attendance.enums.AttendanceErrorCode;
import com.forward.hrms.commons.approval.autoconfig.ApprovalPermissions;
import com.forward.hrms.commons.core.context.CurrentUser;
import com.forward.hrms.commons.core.error.ForbiddenError;
import com.forward.hrms.commons.core.error.NotFoundError;
import com.forward.hrms.commons.core.error.UnprocessableError;

import lombok.RequiredArgsConstructor;

import org.springframework.stereotype.Component;

/**
 * Authorizes on-behalf submissions for leave / overtime / regularization.
 * The submit endpoints accept an optional {@code onBehalfOf} employee id;
 * this resolver translates that into the effective target employee plus a
 * {@link Mode} that drives stage auto-approval downstream.
 *
 * <p>Authority rules, in order:
 * <ul>
 *   <li>{@code onBehalfOf} null or equal to the caller's own employee id
 *       → {@link Mode#SELF} (no auto-approval; normal flow).</li>
 *   <li>Caller has {@code attendance.approval.override} → {@link Mode#HR_OVERRIDE}
 *       (every stage auto-approved; request lands terminal-APPROVED).</li>
 *   <li>Caller is the target's direct manager (target's
 *       {@code manager_id} == caller's employee id) → {@link Mode#MANAGER_ON_BEHALF}
 *       (MANAGER stage auto-approved; flow advances to HR for ratification).</li>
 *   <li>Caller holds the request type's {@code team.request.generation.*}
 *       permission and sits in the target's department →
 *       {@link Mode#TEAM_ON_BEHALF}. Nothing is auto-approved, and the chain
 *       opens against the <em>caller's own</em> manager (see
 *       {@link Resolution#approvalAnchorEmployeeId()}) — the department head
 *       the clerk supports owns the decision, not each target's manager.</li>
 *   <li>Otherwise → 403 {@code ON_BEHALF_NOT_AUTHORIZED}.</li>
 * </ul>
 *
 * <p>The target lookup goes through {@link EmployeeClient#getByIdOptional},
 * which is backed by the authenticated-only {@code /employees/by-ids} —
 * neither MANAGER (V0024) nor TEAM_REQUEST_GENERATION holds
 * {@code employee.read}.
 */
@Component
@RequiredArgsConstructor
public class OnBehalfResolver {

    /** Per-request-type team-generation permissions; see auth V0030. */
    public static final String TEAM_GEN_LEAVE = "team.request.generation.leave";
    public static final String TEAM_GEN_SHORT_LEAVE = "team.request.generation.shortLeave";
    public static final String TEAM_GEN_OVERTIME = "team.request.generation.overtime";
    public static final String TEAM_GEN_REGULARIZATION = "team.request.generation.regularization";

    private final EmployeeClient employees;
    private final CallerContext caller;
    private final ApprovalPermissions approvalPermissions;

    public enum Mode { SELF, TEAM_ON_BEHALF, MANAGER_ON_BEHALF, HR_OVERRIDE }

    /**
     * @param callerEmployeeId the logged-in user's own employee id
     * @param target           the resolved target employee, or {@code null} on
     *                         {@link Mode#SELF} (callers already hold their own row)
     */
    public record Resolution(long callerEmployeeId, long targetEmployeeId, Mode mode,
                             EmployeeClient.EmployeeView target) {
        /** True when the request is being raised for someone other than the caller. */
        public boolean isOnBehalf() { return mode != Mode.SELF; }

        /**
         * True when the submitter's own authority stands in for an approval.
         * TEAM_ON_BEHALF deliberately does not: the clerk raising the request
         * has no approval authority, so the manager stage stays PENDING.
         */
        public boolean autoApproves() {
            return mode == Mode.MANAGER_ON_BEHALF || mode == Mode.HR_OVERRIDE;
        }

        /**
         * The employee whose manager anchors the MANAGER stage — which is not
         * always the employee the request is for.
         *
         * <p>A team-request generator files for their whole department, so the
         * request goes to <em>their own</em> manager (the department head they
         * support), not to each target's manager. Every other mode anchors on
         * the target: a self-submission goes to one's own manager, and the two
         * auto-approving modes close that stage immediately anyway.
         *
         * <p>Pass this to {@code ApprovalService.openChain}; pass
         * {@link #targetEmployeeId()} to the request row itself. They differ
         * only for TEAM_ON_BEHALF, and conflating them routes a clerk's
         * submissions to the wrong approver.
         */
        public long approvalAnchorEmployeeId() {
            return mode == Mode.TEAM_ON_BEHALF ? callerEmployeeId : targetEmployeeId;
        }
    }

    public Resolution resolve(long callerEmployeeId, Long onBehalfOf, String teamGenerationPermission) {
        if (onBehalfOf == null || onBehalfOf == callerEmployeeId) {
            return new Resolution(callerEmployeeId, callerEmployeeId, Mode.SELF, null);
        }
        CurrentUser user = caller.requireUser();
        boolean override = user.hasPermission(approvalPermissions.override());
        EmployeeClient.EmployeeView target = employees.getByIdOptional(onBehalfOf).orElse(null);
        if (target == null) {
            // Only an override holder is entitled to tell "no such employee"
            // apart from "not yours". For everyone else both answer 403, so
            // the endpoint can't be walked to enumerate valid employee ids.
            if (override) {
                throw new NotFoundError(AttendanceErrorCode.EMPLOYEE_NOT_FOUND,
                        "target employee not found");
            }
            throw notAuthorized(teamGenerationPermission);
        }
        Mode mode = authorize(callerEmployeeId, user, target, override, teamGenerationPermission);
        // Deliberately after the authority decision: the message names the
        // target's employment status, which an unauthorized caller must not
        // be able to read off a 422.
        requireActiveTarget(target, onBehalfOf);
        return new Resolution(callerEmployeeId, onBehalfOf, mode, target);
    }

    /**
     * Assert the caller may look at {@code employeeId} because they share a
     * department — the read-side guard for surfaces that list a colleague's
     * data while a request is being raised for them.
     *
     * <p>Reading your own row always passes. Everything else needs both
     * department ids present and equal; a missing employee is reported as 403
     * rather than 404 so the endpoint can't be walked to enumerate ids.
     *
     * <p>Weaker than {@link #resolve}: it asks only "same department", not
     * "may you act for them". Use {@code resolve} on anything that writes.
     */
    public void requireSameDepartmentAsCaller(long callerEmployeeId, long employeeId) {
        if (employeeId == callerEmployeeId) return;
        EmployeeClient.EmployeeView target = employees.getByIdOptional(employeeId).orElse(null);
        if (target == null || !sharesDepartmentWithCaller(target)) {
            throw new ForbiddenError(AttendanceErrorCode.ON_BEHALF_NOT_AUTHORIZED,
                    "employee is not in the caller's department");
        }
    }

    private Mode authorize(long callerEmployeeId, CurrentUser user, EmployeeClient.EmployeeView target,
                           boolean override, String teamGenerationPermission) {
        if (override) {
            return Mode.HR_OVERRIDE;
        }
        if (target.managerId() != null && target.managerId() == callerEmployeeId) {
            return Mode.MANAGER_ON_BEHALF;
        }
        if (user.hasPermission(teamGenerationPermission) && sharesDepartmentWithCaller(target)) {
            return Mode.TEAM_ON_BEHALF;
        }
        throw notAuthorized(teamGenerationPermission);
    }

    private ForbiddenError notAuthorized(String teamGenerationPermission) {
        return new ForbiddenError(AttendanceErrorCode.ON_BEHALF_NOT_AUTHORIZED,
                "caller is not the target's manager, holds neither " + teamGenerationPermission
                        + " for the target's department nor " + approvalPermissions.override());
    }

    /**
     * Both department ids must be present and equal. A null on either side
     * is never a match — otherwise two employees with no department set
     * would authorize each other.
     */
    private boolean sharesDepartmentWithCaller(EmployeeClient.EmployeeView target) {
        Long callerDept = caller.currentEmployee()
                .map(EmployeeClient.EmployeeView::departmentId)
                .orElse(null);
        return callerDept != null && callerDept.equals(target.departmentId());
    }

    private static void requireActiveTarget(EmployeeClient.EmployeeView target, long onBehalfOf) {
        String status = target.employmentStatus();
        if (!"ACTIVE".equals(status) && !"ON_NOTICE".equals(status)) {
            throw new UnprocessableError(AttendanceErrorCode.EMPLOYEE_NOT_ACTIVE,
                    "employee status " + status + " cannot submit leave for employee id " + onBehalfOf);
        }
    }
}
