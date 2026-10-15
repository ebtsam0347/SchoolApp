package com.forward.hrms.commons.core.time;

import org.junit.jupiter.api.Test;

import java.time.LocalDate;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Pakistan FY runs July → June; the FY label is the calendar year of
 * the closing month (FY26 = 2025-07-01 → 2026-06-30). Anchors §0.5.B.1
 * so a future contributor doesn't accidentally use the calendar-year
 * convention.
 */
class PakistanFiscalYearTest {

    @Test
    void july_starts_next_fy() {
        // 2025-07-01 → FY26
        assertEquals(2026, PakistanFiscalYear.of(LocalDate.of(2025, 7, 1)));
    }

    @Test
    void june_30_is_fy_close() {
        // 2026-06-30 → FY26
        assertEquals(2026, PakistanFiscalYear.of(LocalDate.of(2026, 6, 30)));
    }

    @Test
    void start_and_end_of_fy() {
        assertEquals(LocalDate.of(2025, 7, 1), PakistanFiscalYear.startOf(2026));
        assertEquals(LocalDate.of(2026, 6, 30), PakistanFiscalYear.endOf(2026));
    }
}
