package com.forward.hrms.employee.service;


import java.time.LocalDate;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import lombok.RequiredArgsConstructor;

/**
 * <p>Format: {@code <prefix>-<YYYY>-<NNNN>} (e.g. {@code FS-2026-0042}).
 * Per-year sequence held in {@code employee_code_sequence}; a single
 * upsert atomically inserts or increments the row, and Postgres'
 * row-level lock serializes concurrent allocations.
 */
@Service
@RequiredArgsConstructor
public class EmployeeCodeService {

    @PersistenceContext
    private final EntityManager em;

    @Value("${hrms.employee.employee-code.prefix.value:FS}")
    private String prefix;

    @Value("${hrms.employee.employee-code.prefix.enabled:false}")
    private boolean prefixEnabled;

    /**
     * §7.employee — bound check on the per-year sequence. The 4-digit
     * %04d format silently overflows past 9999 (becomes 5+ digits and
     * breaks downstream parsers / ID validators that assume a fixed
     * length). Throw a 422 instead so HR notices and bumps the format
     * before silent corruption.
     */
    private static final int MAX_PER_YEAR = 9999;

    // new method with switch/toggle to support both approaches: with or without prefix
    @Transactional(propagation = Propagation.MANDATORY)
    public String allocate() {

        // new code
        Number next = (Number) em.createNativeQuery("""
                update employee_code_sequence
                set last_value = last_value + 1
                where id = 1
                returning last_value
                """)
                .getSingleResult();

        long value = next.longValue();

        // if prefix not enabled means false ,then return just legacy employee code  like 13411, 13412 etc.
        if (!prefixEnabled) {
            return String.valueOf(value);
        }

        // otherwise return employee code with Prefix Format
        int year = LocalDate.now().getYear();
        return "%s-%d-%d".formatted(prefix, year, value);
    }


    // Old code for just Prefix approach to generate employees codes / card numbers like FS-2026-0001
//    @Transactional(propagation = Propagation.MANDATORY)
//    public String allocate() {
//        int year = LocalDate.now().getYear();
//        Number next = (Number) em.createNativeQuery("""
//                insert into employee_code_sequence (year, last_value)
//                values (:year, 1)
//                on conflict (year) do update
//                  set last_value = employee_code_sequence.last_value + 1
//                returning last_value
//                """)
//                .setParameter("year", year)
//                .getSingleResult();
//        int v = next.intValue();
//        if (v > MAX_PER_YEAR) {
//            throw new com.forward.hrms.commons.core.error.ConflictError(
//                    com.forward.hrms.employee.enums.EmployeeErrorCode.EMPLOYEE_CODE_EXHAUSTED,
//                    "employee code sequence for " + year + " exhausted at " + MAX_PER_YEAR
//                            + "; HR must widen the format before more onboarding");
//        }
//        return "%s-%d-%04d".formatted(prefix, year, v);
//    }


}
