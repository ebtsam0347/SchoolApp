package com.forward.hrms.bff.service;

import com.forward.hrms.bff.client.EmployeeClient;
import com.forward.hrms.employee.dto.response.EmployeeDto;
import com.forward.hrms.bff.dto.response.PersonaHints;
import com.forward.hrms.commons.core.error.UpstreamError;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import org.springframework.stereotype.Service;
import org.springframework.web.client.HttpClientErrorException;

/**
 * Resolves {@link PersonaHints} for the signed-in caller. The only piece
 * not derivable from {@code /auth/me} is the manager signal — does this
 * user have any direct reports — and {@code EmployeeDto} already carries
 * {@code directReportsCount} on every read, so a single call to
 * {@code /employees/me} answers it.
 *
 * <p>Soft-fails to {@link PersonaHints#none()} when the caller has no
 * ESS-linked employee record (404 from {@code /employees/me}) or when
 * employee-service is unreachable. Persona hints are advisory; missing
 * them shouldn't break sign-in.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PersonaHintsService {

    private final EmployeeClient employees;

    public PersonaHints hints() {
        EmployeeDto me;
        try {
            me = employees.me();
        } catch (UpstreamError ex) {
            // 404 (no ESS-linked employee) → no direct reports.
            // Other failures: log and fall through to none() so the FE
            // can still resolve a persona on a degraded employee-service.
            if (ex.getCause() instanceof HttpClientErrorException.NotFound) {
                return PersonaHints.none();
            }
            log.warn("persona-hints: employee /me failed: {}", ex.getMessage());
            return PersonaHints.none();
        }
        if (me == null || me.directReportsCount() == null) return PersonaHints.none();
        int count = me.directReportsCount();
        return new PersonaHints(count > 0, count);
    }
}
