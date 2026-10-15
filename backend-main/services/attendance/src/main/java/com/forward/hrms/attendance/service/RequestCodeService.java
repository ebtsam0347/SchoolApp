package com.forward.hrms.attendance.service;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;

import lombok.RequiredArgsConstructor;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;

/**
 * Allocates the next human-readable code for an approval request
 * (leave / overtime / regularization).
 *
 * <p>Format: {@code <prefix>-<YYYY>-<NNNN>} (e.g. {@code LR-2026-0042}).
 * Per-year, per-prefix sequence held in {@code request_code_sequence};
 * a single upsert atomically inserts or increments the row, and
 * Postgres' row-level lock serializes concurrent allocations. Same
 * pattern as {@code EmployeeCodeService} in employee-service.
 */
@Service
@RequiredArgsConstructor
public class RequestCodeService {

    @PersistenceContext
    private final EntityManager em;

    @Transactional(propagation = Propagation.MANDATORY)
    public String allocate(String prefix) {
        int year = LocalDate.now().getYear();
        Number next = (Number) em.createNativeQuery("""
                insert into request_code_sequence (request_type, year, last_value)
                values (:t, :y, 1)
                on conflict (request_type, year) do update
                  set last_value = request_code_sequence.last_value + 1
                returning last_value
                """)
                .setParameter("t", prefix)
                .setParameter("y", year)
                .getSingleResult();
        return "%s-%d-%04d".formatted(prefix, year, next.intValue());
    }
}
