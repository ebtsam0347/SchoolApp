package com.forward.hrms.attendance.service;

import com.forward.hrms.attendance.entity.LeaveType;
import com.forward.hrms.attendance.enums.LeaveCycleAnchor;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;

/**
 * Pure cycle-window + allocation math for leave entitlements. No state,
 * no Spring bean — {@link LeaveBalanceService} drives it.
 *
 * <ul>
 *   <li>{@code JOINING} — anniversary window anchored on the employee's
 *       joining month/day (Feb-29 joiners fall back to Feb-28 in
 *       non-leap years).</li>
 *   <li>{@code FISCAL} — Jul 1 → Jun 30 straddle containing the date.</li>
 *   <li>{@code NONE} (or JOINING with no known joining date) — calendar
 *       year Jan 1 → Dec 31.</li>
 * </ul>
 */
public final class LeaveCycleResolver {

    private static final BigDecimal TWO = BigDecimal.valueOf(2);

    private LeaveCycleResolver() {}

    /** Inclusive [start, end] cycle window containing {@code on}. */
    public record Window(LocalDate start, LocalDate end) {
        public boolean contains(LocalDate d) {
            return !d.isBefore(start) && !d.isAfter(end);
        }
    }

    public static Window windowFor(LeaveCycleAnchor anchor, LocalDate joiningDate, LocalDate on) {
        if (anchor == LeaveCycleAnchor.JOINING && joiningDate != null) {
            LocalDate anchorThisYear = anniversary(joiningDate, on.getYear());
            LocalDate start = on.isBefore(anchorThisYear)
                    ? anniversary(joiningDate, on.getYear() - 1)
                    : anchorThisYear;
            LocalDate end = anniversary(joiningDate, start.getYear() + 1).minusDays(1);
            return new Window(start, end);
        }
        if (anchor == LeaveCycleAnchor.FISCAL) {
            int startYear = on.getMonthValue() >= 7 ? on.getYear() : on.getYear() - 1;
            return new Window(LocalDate.of(startYear, 7, 1), LocalDate.of(startYear + 1, 6, 30));
        }
        // NONE, or JOINING without a known joining date: calendar year.
        return new Window(LocalDate.of(on.getYear(), 1, 1), LocalDate.of(on.getYear(), 12, 31));
    }

    /**
     * Allocation for a freshly-opened cycle. JOINING / NONE always grant
     * the full default; FISCAL prorates only the entry cycle (the one
     * containing the joining date) when {@code prorateOnEntry} is set.
     */
    public static BigDecimal allocationFor(LeaveType type, LocalDate joiningDate, Window window) {
        BigDecimal full = type.getDefaultPerYear();
        boolean entryCycle = joiningDate != null && window.contains(joiningDate);
        if (type.getCycleAnchor() == LeaveCycleAnchor.FISCAL && type.isProrateOnEntry() && entryCycle) {
            return prorate(full, joiningDate, window);
        }
        return full;
    }

    /**
     * {@code full × remainingDays / fullDays}, rounded UP to the nearest
     * half-day (the system's leave granularity; employee-favorable, matches
     * the project round-up doctrine).
     */
    static BigDecimal prorate(BigDecimal full, LocalDate joiningDate, Window window) {
        long fullDays = ChronoUnit.DAYS.between(window.start(), window.end()) + 1;
        long remainingDays = ChronoUnit.DAYS.between(joiningDate, window.end()) + 1;
        if (remainingDays >= fullDays) return full;
        if (remainingDays <= 0) return BigDecimal.ZERO;
        BigDecimal raw = full.multiply(BigDecimal.valueOf(remainingDays))
                .divide(BigDecimal.valueOf(fullDays), 6, RoundingMode.HALF_UP);
        return roundUpToHalf(raw);
    }

    /** Round up to the nearest 0.5 (e.g. 3.21 → 3.50, 3.50 → 3.50). */
    static BigDecimal roundUpToHalf(BigDecimal v) {
        return v.multiply(TWO).setScale(0, RoundingMode.CEILING)
                .divide(TWO).setScale(2, RoundingMode.UNNECESSARY);
    }

    /** Joining anniversary in {@code year}, clamping Feb 29 to Feb 28 in non-leap years. */
    private static LocalDate anniversary(LocalDate joiningDate, int year) {
        if (joiningDate.getMonthValue() == 2 && joiningDate.getDayOfMonth() == 29
                && !java.time.Year.isLeap(year)) {
            return LocalDate.of(year, 2, 28);
        }
        return LocalDate.of(year, joiningDate.getMonthValue(), joiningDate.getDayOfMonth());
    }
}
