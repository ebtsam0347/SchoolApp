package com.forward.hrms.attendance.service;

import com.forward.hrms.attendance.entity.AttendancePunch;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Pins the day's check-out derivation: a repeat scan within
 * {@link AttendanceRecomputeService#DOUBLE_TAP_GAP} of the check-in is not a
 * check-out, so a double tap at arrival leaves the day open.
 */
class PunchCheckOutTest {

    private static final LocalDateTime OCT_8_0738 = LocalDateTime.of(2026, 10, 8, 7, 38, 5);

    @Test
    void lonePunchIsOnlyACheckIn() {
        assertThat(checkOut(OCT_8_0738)).isNull();
    }

    @Test
    void doubleTapAtArrivalIsNotACheckOut() {
        assertThat(checkOut(OCT_8_0738, OCT_8_0738.plusSeconds(35))).isNull();
    }

    @Test
    void repeatScanRightAtTheGapIsStillADoubleTap() {
        assertThat(checkOut(OCT_8_0738, OCT_8_0738.plus(AttendanceRecomputeService.DOUBLE_TAP_GAP))).isNull();
    }

    @Test
    void punchPastTheGapIsACheckOut() {
        LocalDateTime out = OCT_8_0738.plus(AttendanceRecomputeService.DOUBLE_TAP_GAP).plusSeconds(1);
        assertThat(checkOut(OCT_8_0738, out)).isEqualTo(out);
    }

    @Test
    void doubleTapAtArrivalWithARealCheckOutKeepsTheCheckOut() {
        LocalDateTime out = LocalDateTime.of(2026, 10, 9, 1, 0);
        assertThat(checkOut(OCT_8_0738, OCT_8_0738.plusSeconds(35), out)).isEqualTo(out);
    }

    @Test
    void doubleTapAtCheckOutTakesTheLastScan() {
        LocalDateTime out = LocalDateTime.of(2026, 10, 8, 17, 0);
        assertThat(checkOut(OCT_8_0738, out, out.plusSeconds(20))).isEqualTo(out.plusSeconds(20));
    }

    @Test
    void firstPunchNearShiftEndIsACheckOutOnly() {
        // 08:00–17:00 shift: check-out-only from 15:00.
        LocalDateTime from = LocalDateTime.of(2026, 10, 8, 15, 0);
        LocalDateTime out = LocalDateTime.of(2026, 10, 8, 17, 5);
        List<AttendancePunch> day = day(out);
        assertThat(AttendanceRecomputeService.checkIn(day, from)).isNull();
        assertThat(AttendanceRecomputeService.checkOut(day, null)).isEqualTo(out);
    }

    @Test
    void firstPunchBeforeTheWindowIsStillACheckIn() {
        LocalDateTime from = LocalDateTime.of(2026, 10, 8, 15, 0);
        assertThat(AttendanceRecomputeService.checkIn(day(OCT_8_0738), from)).isEqualTo(OCT_8_0738);
    }

    private static LocalDateTime checkOut(LocalDateTime... times) {
        List<AttendancePunch> day = day(times);
        return AttendanceRecomputeService.checkOut(day, AttendanceRecomputeService.checkIn(day, null));
    }

    private static List<AttendancePunch> day(LocalDateTime... times) {
        return Arrays.stream(times).map(t -> {
            AttendancePunch p = new AttendancePunch();
            p.setPunchedAt(t);
            return p;
        }).toList();
    }
}
