package com.forward.hrms.commons.core.time;

import java.time.LocalDate;

/**
 * Pakistan fiscal-year math. The ZONE constant that used to live on
 * the prior {@code Pakistan} helper is gone — under the trust-server
 * doctrine (CLAUDE.md, §0.5.B) every JVM and Postgres process runs
 * {@code TZ=Asia/Karachi}, so {@code LocalDate.now()} /
 * {@code LocalDateTime.now()} are already wall-clock Pakistan time
 * and need no explicit zone.
 */
public final class PakistanFiscalYear {

    private PakistanFiscalYear() {}

    /**
     * Pakistan fiscal year is Jul–Jun. We name a fiscal year by its
     * ENDING calendar year — FY 2025-26 = the year ending June 2026,
     * represented as the int {@code 2026}.
     */
    public static int of(LocalDate date) {
        return date.getMonthValue() >= 7 ? date.getYear() + 1 : date.getYear();
    }

    public static LocalDate startOf(int fyEndingYear) {
        return LocalDate.of(fyEndingYear - 1, 7, 1);
    }

    public static LocalDate endOf(int fyEndingYear) {
        return LocalDate.of(fyEndingYear, 6, 30);
    }
}
