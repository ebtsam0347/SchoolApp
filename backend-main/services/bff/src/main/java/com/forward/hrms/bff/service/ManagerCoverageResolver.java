package com.forward.hrms.bff.service;

import com.forward.hrms.bff.client.AttendanceClient;
import com.forward.hrms.bff.client.EmployeeClient;
import com.forward.hrms.attendance.dto.response.DelegationDto;
import com.forward.hrms.employee.dto.response.EmployeeIdentitySummary;
import com.forward.hrms.employee.dto.response.EmployeeDto;
import com.forward.hrms.commons.core.error.CommonErrorCode;
import com.forward.hrms.commons.core.error.ForbiddenError;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Computes the set of employees a caller has authority over for a given
 * date — direct reports plus anyone whose manager has delegated approval
 * authority to the caller. Used as the gate for the manager-team
 * attendance composites; if the resulting set is empty, callers get 403
 * (the FE drops back to the ESS view).
 *
 * <p>Delegation rows store user ids on both sides. We resolve delegators
 * to employees via {@code /employees/by-user-ids}, then fetch each
 * delegator's direct reports and union them with the caller's own.
 *
 * <p>Identity lookups are not cached. Onboarding-scale traffic on this
 * endpoint is one call per page-paint; if it later becomes hot we can
 * add a Caffeine cache keyed by (callerEmployeeId, date).
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ManagerCoverageResolver {

    private final EmployeeClient employees;
    private final AttendanceClient attendance;

    /**
     * Build the coverage map for the calling user on the supplied date.
     * Returns a map keyed by employeeId so callers can iterate ids and
     * project EmployeeViews in the same pass.
     */
    public Coverage resolve(LocalDate on) {
        EmployeeDto me = employees.me();
        if (me == null) {
            // /employees/me 404'd — caller has no ESS-linked employee record.
            // No direct reports possible; delegations are also keyed off
            // userId so without an identity there's nothing to resolve.
            return Coverage.empty(null);
        }

        Map<Long, EmployeeDto> covered = new LinkedHashMap<>();
        for (EmployeeDto r : safe(employees.myTeam())) covered.put(r.id(), r);

        for (EmployeeDto r : delegatedReports(on)) covered.putIfAbsent(r.id(), r);

        return new Coverage(me, covered);
    }

    /**
     * Throw 403 unless the caller covers {@code targetEmployeeId} on
     * {@code on}. Used to gate the per-employee month endpoint.
     */
    public Coverage requireCoverage(long targetEmployeeId, LocalDate on) {
        Coverage c = resolve(on);
        if (!c.covers(targetEmployeeId)) {
            throw new ForbiddenError(CommonErrorCode.FORBIDDEN,
                    "caller does not manage employee " + targetEmployeeId
                            + " (and holds no active delegation covering them)");
        }
        return c;
    }

    private List<EmployeeDto> delegatedReports(LocalDate on) {
        List<DelegationDto> active = safe(attendance.myDelegations("ACTIVE", on));
        if (active.isEmpty()) return List.of();

        List<Long> delegatorUserIds = new ArrayList<>(active.size());
        for (DelegationDto d : active) delegatorUserIds.add(d.delegatorId());

        List<EmployeeIdentitySummary> identities = safe(employees.identitiesByUserIds(delegatorUserIds));
        if (identities.isEmpty()) return List.of();

        // Each delegator's whole direct-reports list rolls up into the caller's
        // coverage. Sequential here — usually 0–2 delegations, parallelism not
        // worth the executor plumbing.
        Map<Long, EmployeeDto> out = new HashMap<>();
        for (EmployeeIdentitySummary delegator : identities) {
            for (EmployeeDto r : safe(employees.directReports(delegator.employeeId()))) {
                out.putIfAbsent(r.id(), r);
            }
        }
        return new ArrayList<>(out.values());
    }

    private static <T> List<T> safe(List<T> xs) {
        return xs == null ? List.of() : xs;
    }

    /**
     * Coverage payload. {@code me} is the caller's own row (or null when
     * the caller has no ESS-linked employee, which is the only case where
     * the BFF returns an empty coverage instead of 404'ing). {@code byId}
     * preserves insertion order: own-team first, delegated-team second.
     */
    public record Coverage(EmployeeDto me, Map<Long, EmployeeDto> byId) {
        public static Coverage empty(EmployeeDto me) {
            return new Coverage(me, Map.of());
        }
        public boolean covers(long employeeId) {
            return byId.containsKey(employeeId);
        }
        public boolean isEmpty() {
            return byId.isEmpty();
        }
    }
}
