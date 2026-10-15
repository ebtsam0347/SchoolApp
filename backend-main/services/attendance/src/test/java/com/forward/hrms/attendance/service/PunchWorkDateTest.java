package com.forward.hrms.attendance.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Pins {@link PunchService#resolveWorkDate}: a work date opens 2h before its
 * shift starts and keeps every punch until the next one opens — so OT past
 * midnight and night-shift check-outs land on the shift's own day, and early
 * arrivals within 2h land on the new day.
 */
class PunchWorkDateTest {

    private static final long EMP = 1L;
    private static final LocalDate OCT_9 = LocalDate.of(2026, 10, 9);
    private static final LocalDate OCT_10 = OCT_9.plusDays(1);

    private final Map<LocalDate, ShiftResolution> roster = new HashMap<>();
    private PunchService service;

    @BeforeEach
    void setUp() {
        ShiftResolver shifts = mock(ShiftResolver.class);
        when(shifts.resolve(anyLong(), any(LocalDate.class)))
                .thenAnswer(inv -> Optional.ofNullable(roster.get(inv.getArgument(1, LocalDate.class))));
        service = new PunchService(null, shifts, null, null, null, null, null, null, null,
                null, null, null, null, null, null);
    }

    private void everyDay(String start, String end) {
        for (int i = -3; i <= 3; i++) roster.put(OCT_9.plusDays(i), shift(start, end));
    }

    private static ShiftResolution shift(String start, String end) {
        return new ShiftResolution(1L, LocalTime.parse(start), LocalTime.parse(end), null, null, 10);
    }

    private LocalDate workDate(LocalDate day, String time) {
        return service.resolveWorkDate(EMP, LocalDateTime.of(day, LocalTime.parse(time)));
    }

    @Test
    void dayShiftPunchesStayOnTheirDay() {
        everyDay("07:45", "16:30");
        assertThat(workDate(OCT_9, "07:45")).isEqualTo(OCT_9);
        assertThat(workDate(OCT_9, "06:00")).isEqualTo(OCT_9);   // early, within 2h
        assertThat(workDate(OCT_9, "05:45")).isEqualTo(OCT_9);   // exactly 2h early
        assertThat(workDate(OCT_9, "10:30")).isEqualTo(OCT_9);   // late arrival
        assertThat(workDate(OCT_9, "16:30")).isEqualTo(OCT_9);
        assertThat(workDate(OCT_9, "23:30")).isEqualTo(OCT_9);   // evening OT
    }

    @Test
    void dayShiftOvertimePastMidnightBelongsToPreviousDay() {
        everyDay("07:45", "16:30");
        assertThat(workDate(OCT_10, "02:00")).isEqualTo(OCT_9);
        assertThat(workDate(OCT_10, "03:00")).isEqualTo(OCT_9);
        assertThat(workDate(OCT_10, "05:44")).isEqualTo(OCT_9);  // just before 10 Oct opens
    }

    @Test
    void nightShiftCheckOutBelongsToTheNightItStarted() {
        everyDay("20:00", "05:00");
        assertThat(workDate(OCT_9, "20:00")).isEqualTo(OCT_9);
        assertThat(workDate(OCT_10, "05:00")).isEqualTo(OCT_9);
        assertThat(workDate(OCT_10, "08:30")).isEqualTo(OCT_9);  // OT after the night shift
        assertThat(workDate(OCT_10, "18:30")).isEqualTo(OCT_10); // early for tonight's shift
    }

    @Test
    void shiftStartingAfterMidnightOpensTheEveningBefore() {
        everyDay("00:30", "08:30");
        assertThat(workDate(OCT_9, "23:00")).isEqualTo(OCT_10);
        assertThat(workDate(OCT_10, "00:30")).isEqualTo(OCT_10);
        assertThat(workDate(OCT_10, "09:00")).isEqualTo(OCT_10);
        assertThat(workDate(OCT_9, "21:00")).isEqualTo(OCT_9);   // before 10 Oct opens
    }

    @Test
    void shiftChangeFromDayToNight() {
        roster.put(OCT_9, shift("07:45", "16:30"));
        roster.put(OCT_10, shift("20:00", "05:00"));
        assertThat(workDate(OCT_10, "02:00")).isEqualTo(OCT_9);  // OT out from the day shift
        assertThat(workDate(OCT_10, "12:00")).isEqualTo(OCT_9);  // still before 10 Oct opens (18:00)
        assertThat(workDate(OCT_10, "19:00")).isEqualTo(OCT_10);
    }

    @Test
    void noShiftFallsBackToCalendarDate() {
        assertThat(workDate(OCT_10, "02:00")).isEqualTo(OCT_10);
        roster.put(OCT_10, shift("07:45", "16:30"));            // first day after joining
        assertThat(workDate(OCT_10, "05:00")).isEqualTo(OCT_10);
    }
}
