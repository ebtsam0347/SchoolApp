package com.forward.hrms.employee.service;

import com.forward.hrms.commons.core.error.ConflictError;
import com.forward.hrms.employee.enums.EmployeeErrorCode;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;

import lombok.RequiredArgsConstructor;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;

/**
 * Format: {@code FT-<YYYY>-<NNNN>}. Per-year sequence held in
 * {@code factory_tour_sequence}; a single upsert atomically inserts or
 * increments the row, and Postgres' row-level lock serializes concurrent
 * allocations. Mirrors {@link EmployeeCodeService}'s documented pattern.
 */
@Service
@RequiredArgsConstructor
public class FactoryTourCodeService {

    private static final String PREFIX = "FT";
    private static final int MAX_PER_YEAR = 9999;

    @PersistenceContext
    private final EntityManager em;

    @Transactional(propagation = Propagation.MANDATORY)
    public String allocate() {
        int year = LocalDate.now().getYear();
        Number next = (Number) em.createNativeQuery("""
                insert into factory_tour_sequence (year, last_value)
                values (:year, 1)
                on conflict (year) do update
                  set last_value = factory_tour_sequence.last_value + 1
                returning last_value
                """)
                .setParameter("year", year)
                .getSingleResult();

        int value = next.intValue();
        if (value > MAX_PER_YEAR) {
            throw new ConflictError(EmployeeErrorCode.TOUR_CODE_EXHAUSTED,
                    "factory tour request-id sequence for " + year + " exhausted at " + MAX_PER_YEAR);
        }
        return "%s-%d-%04d".formatted(PREFIX, year, value);
    }
}
