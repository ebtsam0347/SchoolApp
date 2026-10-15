package com.forward.hrms.employee.entity.travel;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit coverage for {@link TravelRequestLeg#effectiveDistanceKm()} — the
 * single source of truth for "which distance drives PER_KM entitlements".
 * The requester's {@code plannedDistanceKm} is the fallback whenever
 * Transport has not recorded a positive actual distance.
 */
class TravelRequestLegEffectiveDistanceTest {

    private static TravelRequestLeg leg(BigDecimal planned, BigDecimal actual) {
        TravelRequestLeg l = new TravelRequestLeg();
        l.setPlannedDistanceKm(planned);
        l.setActualDistanceKm(actual);
        return l;
    }

    @Test
    void nullActual_fallsBackToPlanned() {
        assertThat(leg(new BigDecimal("120.00"), null).effectiveDistanceKm())
                .isEqualByComparingTo("120.00");
    }

    @Test
    void zeroActual_isTreatedAsNotSupplied() {
        assertThat(leg(new BigDecimal("120.00"), BigDecimal.ZERO).effectiveDistanceKm())
                .isEqualByComparingTo("120.00");
    }

    @Test
    void negativeActual_isTreatedAsNotSupplied() {
        assertThat(leg(new BigDecimal("120.00"), new BigDecimal("-5.00")).effectiveDistanceKm())
                .isEqualByComparingTo("120.00");
    }

    @Test
    void positiveActual_overridesPlanned() {
        assertThat(leg(new BigDecimal("120.00"), new BigDecimal("145.00")).effectiveDistanceKm())
                .isEqualByComparingTo("145.00");
    }

    @Test
    void bothNull_isNull() {
        assertThat(leg(null, null).effectiveDistanceKm()).isNull();
    }
}
