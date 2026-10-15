package com.forward.hrms.employee.service.travel;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;

/**
 * Allocates human-readable Travel identifiers:
 * <ul>
 *     <li>{@code TR-{YYYY}-{NNNNNN}}  - travel requests</li>
 *     <li>{@code ADV-{YYYY}-{NNNNNN}} - travel advances</li>
 *     <li>{@code SET-{YYYY}-{NNNNNN}} - travel settlements</li>
 * </ul>
 *
 * <p>Per-year, per-scope counters live in {@code travel_number_sequence}
 * (V0610). A single upsert atomically inserts or increments the row, and
 * Postgres' row-level lock serializes concurrent allocations. Same
 * pattern as {@link com.forward.hrms.employee.service.EmployeeCodeService}.
 *
 * <p>{@code Propagation.MANDATORY}: allocation must run inside the
 * caller's business transaction so the counter row commits together with
 * the record that uses the number.
 */
@Service
@RequiredArgsConstructor
public class TravelNumberService {

    @PersistenceContext
    private final EntityManager em;

    @Transactional(propagation = Propagation.MANDATORY)
    public String nextTravelRequestNumber() {
        return allocate("REQUEST", "TR");
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public String nextAdvanceNumber() {
        return allocate("ADVANCE", "ADV");
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public String nextSettlementNumber() {
        return allocate("SETTLEMENT", "SET");
    }

    private String allocate(String scope, String prefix) {
        int year = LocalDate.now().getYear();

        Number next = (Number) em.createNativeQuery("""
                        insert into travel_number_sequence (scope, year, last_value)
                        values (:scope, :year, 1)
                        on conflict (scope, year) do update
                          set last_value = travel_number_sequence.last_value + 1
                        returning last_value
                        """)
                .setParameter("scope", scope)
                .setParameter("year", year)
                .getSingleResult();

        // %06d keeps the number a fixed 6 digits (TR-2026-000125); it
        // silently widens past 999999, which no realistic yearly volume
        // reaches.
        return "%s-%d-%06d".formatted(prefix, year, next.longValue());
    }
}
