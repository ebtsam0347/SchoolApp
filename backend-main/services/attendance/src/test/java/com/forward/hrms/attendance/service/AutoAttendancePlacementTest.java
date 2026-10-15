package com.forward.hrms.attendance.service;

import com.forward.hrms.attendance.dto.request.AutoAttendanceRequest;

import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.HashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Pins {@link AutoAttendanceService#place}: each work date's check-in /
 * check-out land on the calendar instants the device puller would attribute
 * to that work date, from the day's own shift.
 */
class AutoAttendancePlacementTest {

    private static final LocalDate OCT_9 = LocalDate.of(2026, 10, 9);
    private static final LocalDate OCT_10 = OCT_9.plusDays(1);

    private final Map<LocalDate, ShiftResolution> roster = new HashMap<>();

    private static ShiftResolution shift(String start, String end) {
        return new ShiftResolution(1L, LocalTime.parse(start), LocalTime.parse(end), null, null, 10);
    }

    private static AutoAttendanceRequest req(String in, String out) {
        return new AutoAttendanceRequest(1L, OCT_9, OCT_9,
                in == null ? null : LocalTime.parse(in), out == null ? null : LocalTime.parse(out),
                false, false, null);
    }

    private static LocalDateTime at(LocalDate d, String t) {
        return d.atTime(LocalTime.parse(t));
    }

    private LocalDateTime[] place(LocalDate d, String in, String out) {
        return AutoAttendanceService.place(d, req(in, out), roster);
    }

    @Test
    void eveningShiftChecksOutNextMorningOnTheSameRecord() {
        roster.put(OCT_9, shift("18:00", "02:00"));
        roster.put(OCT_10, shift("18:00", "02:00"));
        assertThat(place(OCT_9, null, null)).containsExactly(at(OCT_9, "18:00"), at(OCT_10, "02:00"));
        assertThat(place(OCT_9, "18:00", "02:00")).containsExactly(at(OCT_9, "18:00"), at(OCT_10, "02:00"));
    }

    @Test
    void dayShiftDefaultsAndOvertimePastMidnight() {
        roster.put(OCT_9, shift("07:45", "16:30"));
        roster.put(OCT_10, shift("07:45", "16:30"));
        assertThat(place(OCT_9, null, null)).containsExactly(at(OCT_9, "07:45"), at(OCT_9, "16:30"));
        assertThat(place(OCT_9, "07:45", "02:00")).containsExactly(at(OCT_9, "07:45"), at(OCT_10, "02:00"));
    }

    @Test
    void shiftStartingAfterMidnightArrivesTheEveningBefore() {
        roster.put(OCT_9, shift("00:30", "08:30"));
        roster.put(OCT_10, shift("00:30", "08:30"));
        assertThat(place(OCT_10, "23:50", "08:30"))
                .containsExactly(at(OCT_9, "23:50"), at(OCT_10, "08:30"));
    }

    @Test
    void eachDayUsesItsOwnShift() {
        roster.put(OCT_9, shift("07:45", "16:30"));
        roster.put(OCT_10, shift("20:00", "05:00"));
        roster.put(OCT_10.plusDays(1), shift("20:00", "05:00"));
        assertThat(place(OCT_9, null, null)).containsExactly(at(OCT_9, "07:45"), at(OCT_9, "16:30"));
        assertThat(place(OCT_10, null, null))
                .containsExactly(at(OCT_10, "20:00"), at(OCT_10.plusDays(1), "05:00"));
    }

    @Test
    void noShiftUsesGivenTimesOrSkips() {
        assertThat(place(OCT_9, "18:00", "02:00")).containsExactly(at(OCT_9, "18:00"), at(OCT_10, "02:00"));
        assertThat(place(OCT_9, null, null)).isNull();
    }

    @Test
    void outNotAfterInIsRejected() {
        roster.put(OCT_9, shift("07:45", "16:30"));
        assertThat(place(OCT_9, "16:00", "09:00")).isNull();
    }
}
