package com.forward.hrms.attendance.service;

import com.forward.hrms.attendance.dto.response.PayrollAttendanceAuditDto;
import com.forward.hrms.attendance.enums.AttendanceStatus;
import com.forward.hrms.attendance.service.PayrollAttendanceClassifier.DayRecord;
import com.forward.hrms.attendance.service.PayrollAttendanceClassifier.LeaveDay;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Pure classification rules. The anchor case is employee 13411's real
 * August 2026 data (dev DB, 2026-09-19): 23 PRESENT rows, one paid CASUAL
 * day on Sat Aug 8, public holidays Aug 14 and Aug 26 — which must
 * reproduce the legacy slip's "Days Worked 26" (31 calendar − 5 Sundays).
 */
class PayrollAttendanceClassifierTest {

    private static final LocalDate FROM = LocalDate.of(2026, 8, 1);
    private static final LocalDate TO = LocalDate.of(2026, 8, 31);
    private static final Set<LocalDate> HOLIDAYS =
            Set.of(LocalDate.of(2026, 8, 14), LocalDate.of(2026, 8, 26));

    private static LocalDate aug(int day) { return LocalDate.of(2026, 8, day); }

    private static Map<LocalDate, DayRecord> present(int... days) {
        Map<LocalDate, DayRecord> m = new HashMap<>();
        for (int d : days) m.put(aug(d), new DayRecord(AttendanceStatus.PRESENT, null));
        return m;
    }

    private static Map<LocalDate, DayRecord> presentAllWorkingDays() {
        Map<LocalDate, DayRecord> m = new HashMap<>();
        for (LocalDate d = FROM; !d.isAfter(TO); d = d.plusDays(1)) {
            if (d.getDayOfWeek() != java.time.DayOfWeek.SUNDAY && !HOLIDAYS.contains(d)) {
                m.put(d, new DayRecord(AttendanceStatus.PRESENT, null));
            }
        }
        return m;
    }

    private static PayrollAttendanceAuditDto classify(LocalDate joined, Map<LocalDate, DayRecord> rec,
                                                      Map<LocalDate, List<LeaveDay>> leaves) {
        return PayrollAttendanceClassifier.classify(FROM, TO, joined, HOLIDAYS, rec, leaves);
    }

    private static LeaveDay leave(String fraction, boolean paid) {
        return new LeaveDay(new BigDecimal(fraction), paid);
    }

    @Test
    void employee13411_august_reproducesLegacyDaysWorked26() {
        Map<LocalDate, DayRecord> rec = present(1, 3, 4, 5, 6, 7, 10, 11, 12, 13, 15, 17, 18, 19, 20, 21,
                22, 24, 25, 27, 28, 29, 31);
        Map<LocalDate, List<LeaveDay>> leaves = Map.of(aug(8), List.of(leave("1", true)));

        PayrollAttendanceAuditDto a = classify(LocalDate.of(2025, 2, 17), rec, leaves);

        assertThat(a.calendarDays()).isEqualTo(31);
        assertThat(a.weeklyOffDays()).isEqualTo(5);      // Sundays 2, 9, 16, 23, 30
        assertThat(a.holidayDays()).isEqualTo(2);        // Aug 14, Aug 26
        assertThat(a.workingDays()).isEqualTo(24);
        assertThat(a.presentDays()).isEqualByComparingTo("23");
        assertThat(a.paidLeaveDays()).isEqualByComparingTo("1");
        assertThat(a.unpaidLeaveDays()).isEqualByComparingTo("0");
        assertThat(a.absentDays()).isEqualByComparingTo("0");
        assertThat(a.lopDays()).isEqualByComparingTo("0");
        assertThat(a.paidDays()).isEqualByComparingTo("26");   // legacy "Days Worked"
        assertThat(a.presentDays().add(a.paidLeaveDays()).intValue() + a.holidayDays()).isEqualTo(26);
    }

    @Test
    void unclosedAttendance_workingDayWithoutRecordOrLeave_isUnrecordedAbsent() {
        Map<LocalDate, DayRecord> rec = presentAllWorkingDays();
        rec.remove(aug(5));
        rec.remove(aug(6));

        PayrollAttendanceAuditDto a = classify(null, rec, Map.of());

        assertThat(a.absentDays()).isEqualByComparingTo("2");
        assertThat(a.unrecordedAbsentDays()).isEqualByComparingTo("2");
        assertThat(a.lopDays()).isEqualByComparingTo("2");
        assertThat(a.paidDays()).isEqualByComparingTo("24");   // 31 - 5 Sundays - 2 LOP
    }

    @Test
    void unpaidLeave_countsAsLop_notAbsent() {
        Map<LocalDate, DayRecord> rec = presentAllWorkingDays();
        rec.remove(aug(10));
        Map<LocalDate, List<LeaveDay>> leaves = Map.of(aug(10), List.of(leave("1", false)));

        PayrollAttendanceAuditDto a = classify(null, rec, leaves);

        assertThat(a.unpaidLeaveDays()).isEqualByComparingTo("1");
        assertThat(a.absentDays()).isEqualByComparingTo("0");
        assertThat(a.unrecordedAbsentDays()).isEqualByComparingTo("0");
        assertThat(a.lopDays()).isEqualByComparingTo("1");
    }

    @Test
    void paidLeave_isNotAbsentAndNotLop() {
        Map<LocalDate, DayRecord> rec = presentAllWorkingDays();
        rec.remove(aug(10));
        Map<LocalDate, List<LeaveDay>> leaves = Map.of(aug(10), List.of(leave("1", true)));

        PayrollAttendanceAuditDto a = classify(null, rec, leaves);

        assertThat(a.paidLeaveDays()).isEqualByComparingTo("1");
        assertThat(a.lopDays()).isEqualByComparingTo("0");
        assertThat(a.paidDays()).isEqualByComparingTo("26");
    }

    @Test
    void halfDayLeaveWithPresentRecord_isHalfPresentHalfLeave() {
        Map<LocalDate, DayRecord> rec = presentAllWorkingDays();
        Map<LocalDate, List<LeaveDay>> paid = Map.of(aug(10), List.of(leave("0.5", true)));
        Map<LocalDate, List<LeaveDay>> unpaid = Map.of(aug(10), List.of(leave("0.5", false)));

        PayrollAttendanceAuditDto p = classify(null, rec, paid);
        assertThat(p.presentDays()).isEqualByComparingTo("23.5");
        assertThat(p.paidLeaveDays()).isEqualByComparingTo("0.5");
        assertThat(p.lopDays()).isEqualByComparingTo("0");

        PayrollAttendanceAuditDto u = classify(null, rec, unpaid);
        assertThat(u.unpaidLeaveDays()).isEqualByComparingTo("0.5");
        assertThat(u.lopDays()).isEqualByComparingTo("0.5");
    }

    @Test
    void fullDayLeaveOnPresentDay_isConflict_presenceWins() {
        Map<LocalDate, DayRecord> rec = presentAllWorkingDays();
        Map<LocalDate, List<LeaveDay>> leaves = Map.of(aug(10), List.of(leave("1", false)));

        PayrollAttendanceAuditDto a = classify(null, rec, leaves);

        assertThat(a.leaveConflictDays()).isEqualTo(1);
        assertThat(a.unpaidLeaveDays()).isEqualByComparingTo("0");
        assertThat(a.lopDays()).isEqualByComparingTo("0");
    }

    @Test
    void halfDayRecordWithoutLeave_isHalfPresentHalfAbsent() {
        Map<LocalDate, DayRecord> rec = presentAllWorkingDays();
        rec.put(aug(10), new DayRecord(AttendanceStatus.HALF_DAY, null));

        PayrollAttendanceAuditDto a = classify(null, rec, Map.of());

        assertThat(a.halfDayRecords()).isEqualTo(1);
        assertThat(a.presentDays()).isEqualByComparingTo("23.5");
        assertThat(a.absentDays()).isEqualByComparingTo("0.5");
        assertThat(a.unrecordedAbsentDays()).isEqualByComparingTo("0");   // a record exists
        assertThat(a.lopDays()).isEqualByComparingTo("0.5");
    }

    @Test
    void closedLeaveRecordWithoutRequestRows_usesRecordPaidFlag() {
        Map<LocalDate, DayRecord> rec = presentAllWorkingDays();
        rec.put(aug(10), new DayRecord(AttendanceStatus.LEAVE, false));
        rec.put(aug(11), new DayRecord(AttendanceStatus.LEAVE, true));

        PayrollAttendanceAuditDto a = classify(null, rec, Map.of());

        assertThat(a.unpaidLeaveDays()).isEqualByComparingTo("1");
        assertThat(a.paidLeaveDays()).isEqualByComparingTo("1");
        assertThat(a.lopDays()).isEqualByComparingTo("1");
    }

    @Test
    void midMonthJoiner_daysBeforeJoiningAreOutsideWindow_notAbsent() {
        // Joins Wed Aug 19. In-window Sundays: 23, 30. Holiday: Aug 26.
        LocalDate joined = aug(19);
        Map<LocalDate, DayRecord> rec = new HashMap<>();
        for (LocalDate d = joined; !d.isAfter(TO); d = d.plusDays(1)) {
            if (d.getDayOfWeek() != java.time.DayOfWeek.SUNDAY && !HOLIDAYS.contains(d)) {
                rec.put(d, new DayRecord(AttendanceStatus.PRESENT, null));
            }
        }

        PayrollAttendanceAuditDto a = classify(joined, rec, Map.of());

        assertThat(a.preJoiningDays()).isEqualTo(18);
        assertThat(a.weeklyOffDays()).isEqualTo(2);
        assertThat(a.holidayDays()).isEqualTo(1);
        assertThat(a.workingDays()).isEqualTo(13 - 2 - 1);
        assertThat(a.absentDays()).isEqualByComparingTo("0");
        assertThat(a.lopDays()).isEqualByComparingTo("0");
        assertThat(a.paidDays()).isEqualByComparingTo("11");   // 13 window days - 2 Sundays
    }

    @Test
    void presentOnSundayOrHoliday_isTrackedSeparately_notAsWorkingPresence() {
        Map<LocalDate, DayRecord> rec = presentAllWorkingDays();
        rec.put(aug(2), new DayRecord(AttendanceStatus.PRESENT, null));    // Sunday
        rec.put(aug(14), new DayRecord(AttendanceStatus.PRESENT, null));   // holiday

        PayrollAttendanceAuditDto a = classify(null, rec, Map.of());

        assertThat(a.presentOnOffDays()).isEqualTo(2);
        assertThat(a.presentDays()).isEqualByComparingTo("24");   // working days only
        assertThat(a.workingDays()).isEqualTo(24);
    }

    @Test
    void everyDateHasExactlyOneClassification() {
        Map<LocalDate, DayRecord> rec = present(1, 3);
        Map<LocalDate, List<LeaveDay>> leaves = Map.of(aug(4), List.of(leave("1", true)),
                aug(5), List.of(leave("1", false)));

        PayrollAttendanceAuditDto a = classify(aug(3), rec, leaves);

        BigDecimal workingAccounted = a.presentDays().add(a.paidLeaveDays()).add(a.unpaidLeaveDays())
                .add(a.absentDays());
        assertThat(workingAccounted).isEqualByComparingTo(BigDecimal.valueOf(a.workingDays()));
        assertThat(a.preJoiningDays() + a.weeklyOffDays() + a.holidayDays() + a.workingDays())
                .isEqualTo(a.calendarDays());
    }
}
