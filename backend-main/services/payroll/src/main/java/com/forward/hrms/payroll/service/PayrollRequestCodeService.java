package com.forward.hrms.payroll.service;


import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;

import lombok.RequiredArgsConstructor;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;

/**
 * Allocates the next human-readable code for a payroll request
 * (permanent advance, short-term advance batch).
 *
 * <p>Format: {@code <prefix>-<YYYY>-<NNNN>} (e.g. {@code PA-2026-0042}).
 * Per-year, per-prefix sequence held in {@code payroll_request_code_sequence};
 * a single upsert atomically inserts or increments the row, and Postgres'
 * row-level lock serializes concurrent allocations. Same pattern as
 * {@code RequestCodeService} in attendance and {@code EmployeeCodeService}
 * in employee-service.
 */
@Service
@RequiredArgsConstructor
public class PayrollRequestCodeService {

    @PersistenceContext
    private final EntityManager em;

    @Transactional(propagation = Propagation.MANDATORY)
    public String allocate(String prefix) {
        int year = LocalDate.now().getYear();
        Number next = (Number) em.createNativeQuery("""
                insert into payroll_request_code_sequence (request_type, year, last_value)
                values (:t, :y, 1)
                on conflict (request_type, year) do update
                  set last_value = payroll_request_code_sequence.last_value + 1
                returning last_value
                """)
                .setParameter("t", prefix)
                .setParameter("y", year)
                .getSingleResult();
        return "%s-%d-%04d".formatted(prefix, year, next.intValue());
    }
}
