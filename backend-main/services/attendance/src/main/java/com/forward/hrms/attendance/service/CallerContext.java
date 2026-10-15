package com.forward.hrms.attendance.service;

import com.forward.hrms.attendance.client.EmployeeClient;
import com.forward.hrms.attendance.enums.AttendanceErrorCode;
import com.forward.hrms.commons.core.context.CurrentUser;
import com.forward.hrms.commons.core.context.CurrentUserHolder;
import com.forward.hrms.commons.core.error.ForbiddenError;
import com.forward.hrms.commons.core.error.UnauthorizedError;

import lombok.RequiredArgsConstructor;

import org.springframework.stereotype.Component;
import org.springframework.web.context.request.RequestAttributes;
import org.springframework.web.context.request.RequestContextHolder;

import java.util.Optional;

/**
 * Resolves the caller's employee row from their JWT user id. Used by
 * /me endpoints and self-service flows. Cached per-request via
 * {@link CurrentUserHolder} — repeated calls within the same request
 * thread hit employee-service once.
 */
@Component
@RequiredArgsConstructor
public class CallerContext {

    /**
     * Request-scoped cache for the resolved caller employee. Uses the
     * Spring request attributes so a single HTTP request shares one
     * upstream lookup — important for the DTO assembler which is called
     * once per row when assembling list responses.
     */
    private static final String CACHE_KEY = "fshrms.attendance.callerEmployee";
    private static final Object NULL_SENTINEL = new Object();

    private final EmployeeClient employees;

    public CurrentUser requireUser() {
        return CurrentUserHolder.get()
                .orElseThrow(() -> new UnauthorizedError(AttendanceErrorCode.NOT_AUTHORIZED, "no current user"));
    }

    public EmployeeClient.EmployeeView requireEmployee() {
        return currentEmployee()
                .orElseThrow(() -> new ForbiddenError(AttendanceErrorCode.EMPLOYEE_NOT_FOUND,
                        "no employee linked to current user"));
    }

    /** Tolerant variant — empty when no current user, no employee link, or no request context. */
    public Optional<EmployeeClient.EmployeeView> currentEmployee() {
        Optional<CurrentUser> user = CurrentUserHolder.get();
        if (user.isEmpty()) return Optional.empty();
        long userId = user.get().userId();

        RequestAttributes attrs = RequestContextHolder.getRequestAttributes();
        if (attrs != null) {
            Object cached = attrs.getAttribute(CACHE_KEY, RequestAttributes.SCOPE_REQUEST);
            if (cached == NULL_SENTINEL) return Optional.empty();
            if (cached instanceof EmployeeClient.EmployeeView ev) return Optional.of(ev);
        }
        Optional<EmployeeClient.EmployeeView> resolved = employees.findByUserId(userId);
        if (attrs != null) {
            attrs.setAttribute(CACHE_KEY,
                    resolved.<Object>map(v -> v).orElse(NULL_SENTINEL),
                    RequestAttributes.SCOPE_REQUEST);
        }
        return resolved;
    }
}
