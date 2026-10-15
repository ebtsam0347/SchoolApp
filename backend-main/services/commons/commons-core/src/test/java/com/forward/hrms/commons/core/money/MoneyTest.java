package com.forward.hrms.commons.core.money;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * Anchors the doctrine: every Money operation that produces a fraction
 * below scale 2 rounds UP to the next paisa (CEILING). Verifies the
 * three sites reviewed by §2.1 land identical output regardless of which
 * helper a caller picks (divide vs. proration vs. multiply).
 */
class MoneyTest {

    @Test
    void of_normalizes_to_scale_2_ceiling() {
        // 100.001 → 100.01 (rounds up at the third decimal)
        assertEquals(new BigDecimal("100.01"), Money.of(new BigDecimal("100.001")).amount());
    }

    @Test
    void divide_rounds_ceiling_at_scale_2() {
        // 100 / 3 = 33.33333… → 33.34 (ceiling at scale 2)
        assertEquals(new BigDecimal("33.34"),
                Money.of(100L).divide(BigDecimal.valueOf(3)).amount());
    }

    @Test
    void proration_rounds_ceiling_at_scale_2() {
        // 50000 × 17 / 30 = 28333.333… → 28333.34
        assertEquals(new BigDecimal("28333.34"),
                Money.of(50_000L).proration(BigDecimal.valueOf(17), BigDecimal.valueOf(30)).amount());
    }

    @Test
    void proration_by_zero_throws() {
        assertThrows(ArithmeticException.class,
                () -> Money.of(100L).proration(BigDecimal.ZERO, BigDecimal.ZERO));
    }

    @Test
    void add_subtract_preserve_scale() {
        Money a = Money.of(new BigDecimal("100.50"));
        Money b = Money.of(new BigDecimal("0.49"));
        assertEquals(new BigDecimal("100.99"), a.add(b).amount());
        assertEquals(new BigDecimal("100.01"), a.subtract(b).amount());
    }
}
