package com.forward.hrms.audit.service;

import com.forward.hrms.commons.core.context.CurrentUser;
import com.forward.hrms.commons.core.context.CurrentUserHolder;
import com.forward.hrms.commons.core.error.ForbiddenError;
import com.forward.hrms.commons.core.error.UnauthorizedError;

import org.springframework.stereotype.Component;

/**
 * Decides whether the current caller may read an entity's user-facing
 * timeline. Starts simple per audit.md §5: HR/ADMIN sees everything; a
 * regular user can read their own user/employee timeline. Manager-of-X
 * checks belong here too once employee-service publishes the relationship.
 */
@Component
public class TimelinePolicy {

    public void check(String entityType, String entityId) {
        CurrentUser user = CurrentUserHolder.get()
                .orElseThrow(() -> new UnauthorizedError("Authentication required."));

        // Org-wide timeline read is gated purely on audit.read — ADMIN holds it,
        // HR deliberately does not (V0015 stripped audit.read from HR; HR reads
        // employee history via employee-service instead). Don't re-open that via
        // a hasRole("HR") shortcut.
        if (user.hasPermission("audit.read")) {
            return;
        }
        if (("user".equals(entityType) || "employee".equals(entityType))
                && entityId.equals(String.valueOf(user.userId()))) {
            return;
        }
        // Approval-request timelines (leave / overtime / regularization) are gated upstream:
        // the BFF must verify the caller can see the request before fetching its timeline.
        // The events themselves carry only action + decision metadata that the request DTO
        // already exposes, so we trust authenticated callers here.
        if (isApprovalRequestType(entityType)) {
            return;
        }
        throw new ForbiddenError("Not allowed to read this timeline.");
    }

    private static boolean isApprovalRequestType(String entityType) {
        return "leave_request".equals(entityType)
                || "overtime_request".equals(entityType)
                || "regularization_request".equals(entityType);
    }
}
