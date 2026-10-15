package com.forward.hrms.payroll.service;

import java.math.BigDecimal;

/**
 * Per-employee attendance numbers fed into compute. Sourced from
 * attendance-service. If attendance is unavailable the snapshot call
 * raises {@code UpstreamError} — never silently substitutes "full
 * month present," which previously paid terminated/absent/sick
 * employees full salary on any attendance hiccup.
 *
 * <p>The first three fields drive monetary computation (LOP-proration,
 * per-day allowance multiplication, OT). The trailing breakdown fields
 * are surfaced on the payslip card and do not affect math:
 *
 * <ul>
 *   <li><b>workingDays</b> — calendar days minus holidays and weekly-offs.</li>
 *   <li><b>paidDays</b> — workingDays minus absentDays (LOP). Decimal so
 *       half-day absents land in their own bucket.</li>
 *   <li><b>absentDays</b> — count of ABSENT plus unpaid LEAVE plus
 *       half-day absents. §7.payroll #23 — unpaid leaves were silently
 *       dropped before; now treated as LOP.</li>
 *   <li><b>holidaysInPeriod</b> — count of HOLIDAY rows in the daily projection.</li>
 *   <li><b>weekendsInPeriod</b> — count of WEEKEND rows. §7.payroll #25 —
 *       was misnamed {@code sundaysInPeriod}; the system supports any
 *       day-of-week as the weekly off (Forward Sports has Friday-off
 *       shifts at some sites).</li>
 * </ul>
 */
public record AttendanceSnapshot(
        BigDecimal lopDays,
        BigDecimal daysPresent,
        BigDecimal otHours,
        Integer workingDays,
        BigDecimal paidDays,
        BigDecimal absentDays,
        Integer holidaysInPeriod,
        Integer weekendsInPeriod
) {
}
