package com.forward.hrms.attendance.service;

import com.forward.hrms.attendance.dto.response.PayrollAttendanceAuditDto;
import com.forward.hrms.attendance.enums.AttendanceStatus;

import java.math.BigDecimal;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Pure per-day attendance classification for a payroll period. Every date
 * in {@code [from, to]} lands in exactly one bucket:
 *
 * <ul>
 *   <li>before the joining date — excluded from the employee's window;</li>
 *   <li>Sunday — weekly off (the organization's weekly off, same rule as
 *       {@code AttendanceRecomputeService.resolveStatus});</li>
 *   <li>public holiday — paid, non-working;</li>
 *   <li>otherwise a working day, split into present / paid leave /
 *       unpaid leave / absent. A {@code HALF_DAY} record or a half-day
 *       leave request contributes 0.5; the remainder is absent unless the
 *       other half is covered.</li>
 * </ul>
 *
 * A full-day approved leave on a day the employee also has a
 * {@code PRESENT} record is a data conflict — presence wins and the day is
 * counted in {@code leaveConflictDays}. A working day with no record and
 * no approved leave is absent, and is also reported as
 * {@code unrecordedAbsentDays} so a period whose attendance was never
 * closed is visible rather than silently paid.
 *
 * <p>{@code paidDays} = window days − weekly offs − LOP, i.e. the legacy
 * "Days Worked" (public holidays are paid). No I/O — unit-testable.
 */
public final class PayrollAttendanceClassifier {

    private static final BigDecimal ONE = BigDecimal.ONE;
    private static final BigDecimal HALF = new BigDecimal("0.5");

    private PayrollAttendanceClassifier() {}

    public record DayRecord(AttendanceStatus status, Boolean paid) {}

    public record LeaveDay(BigDecimal fraction, boolean paid) {}

    public static PayrollAttendanceAuditDto classify(LocalDate from, LocalDate to, LocalDate joiningDate,
                                                     Set<LocalDate> holidays,
                                                     Map<LocalDate, DayRecord> records,
                                                     Map<LocalDate, List<LeaveDay>> leaves) {
        int calendarDays = 0, preJoining = 0, weeklyOff = 0, holidayDays = 0, working = 0;
        int halfDayRecords = 0, presentOnOff = 0, conflicts = 0;
        BigDecimal present = BigDecimal.ZERO, paidLeave = BigDecimal.ZERO, unpaidLeave = BigDecimal.ZERO;
        BigDecimal absent = BigDecimal.ZERO, unrecorded = BigDecimal.ZERO;

        for (LocalDate d = from; !d.isAfter(to); d = d.plusDays(1)) {
            calendarDays++;
            if (joiningDate != null && d.isBefore(joiningDate)) { preJoining++; continue; }

            DayRecord rec = records.get(d);
            BigDecimal presence = presenceOf(rec);
            if (rec != null && rec.status() == AttendanceStatus.HALF_DAY) halfDayRecords++;

            if (d.getDayOfWeek() == DayOfWeek.SUNDAY) {
                weeklyOff++;
                if (presence.signum() > 0) presentOnOff++;
                continue;
            }
            if (holidays.contains(d)) {
                holidayDays++;
                if (presence.signum() > 0) presentOnOff++;
                continue;
            }

            working++;
            List<LeaveDay> dayLeaves = leaves.getOrDefault(d, List.of());
            boolean anyLeave = !dayLeaves.isEmpty();
            // A PRESENT record on a day with an approved half-day leave means
            // the employee worked the other half: 0.5 present + 0.5 leave,
            // not a full presence that would hide (e.g.) an unpaid half-day.
            BigDecimal halfLeave = dayLeaves.stream().map(LeaveDay::fraction)
                    .filter(f -> f.compareTo(ONE) < 0).reduce(BigDecimal.ZERO, BigDecimal::add);
            if (presence.compareTo(ONE) == 0 && halfLeave.signum() > 0) {
                presence = ONE.subtract(halfLeave.min(ONE));
            }
            present = present.add(presence);
            BigDecimal remaining = ONE.subtract(presence);
            for (LeaveDay lv : dayLeaves) {
                BigDecimal applied = lv.fraction().min(remaining);
                if (applied.signum() <= 0) {
                    if (presence.compareTo(ONE) == 0 && lv.fraction().compareTo(ONE) >= 0) conflicts++;
                    continue;
                }
                if (lv.paid()) paidLeave = paidLeave.add(applied); else unpaidLeave = unpaidLeave.add(applied);
                remaining = remaining.subtract(applied);
            }
            if (!anyLeave && rec != null && rec.status() == AttendanceStatus.LEAVE && remaining.signum() > 0) {
                boolean recPaid = rec.paid() == null || rec.paid();
                if (recPaid) paidLeave = paidLeave.add(remaining); else unpaidLeave = unpaidLeave.add(remaining);
                remaining = BigDecimal.ZERO;
            }
            if (remaining.signum() > 0) {
                absent = absent.add(remaining);
                if (rec == null && !anyLeave) unrecorded = unrecorded.add(remaining);
            }
        }

        BigDecimal lop = absent.add(unpaidLeave);
        int windowDays = calendarDays - preJoining;
        BigDecimal paidDays = BigDecimal.valueOf(windowDays - weeklyOff).subtract(lop);
        return new PayrollAttendanceAuditDto(calendarDays, preJoining, weeklyOff, holidayDays, working,
                present, paidLeave, unpaidLeave, absent, unrecorded,
                halfDayRecords, presentOnOff, conflicts, lop, paidDays.max(BigDecimal.ZERO));
    }

    private static BigDecimal presenceOf(DayRecord rec) {
        if (rec == null) return BigDecimal.ZERO;
        return switch (rec.status()) {
            case PRESENT -> ONE;
            case HALF_DAY -> HALF;
            default -> BigDecimal.ZERO;
        };
    }
}
