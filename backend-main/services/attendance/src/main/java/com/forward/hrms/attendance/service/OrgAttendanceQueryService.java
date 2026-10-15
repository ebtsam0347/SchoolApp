package com.forward.hrms.attendance.service;

import com.forward.hrms.attendance.dto.response.OrgAttendanceDayDto;
import com.forward.hrms.attendance.dto.response.OrgPulseDto;
import com.forward.hrms.attendance.enums.AttendanceStatus;
import com.forward.hrms.attendance.enums.ShiftPeriod;
import com.forward.hrms.attendance.repository.AttendanceRecordRepository;

import lombok.RequiredArgsConstructor;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Org-wide attendance aggregates. One JPQL group-by per call; the BFF
 * dashboards consume these directly so they don't reduce per-employee
 * rows in JS.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class OrgAttendanceQueryService {

    private final AttendanceRecordRepository records;

    /**
     * Status roll-up for a single date. {@code total} counts every row that
     * has a record — days with no record (e.g. employees who haven't punched
     * yet and the nightly close hasn't run) are simply absent from the sum.
     */
    @Transactional(readOnly = true)
    public OrgPulseDto pulse(LocalDate asOf) {
        return pulse(asOf, null);
    }

    /** {@link #pulse(LocalDate)} limited to records on a {@code period} shift; null = all. */
    @Transactional(readOnly = true)
    public OrgPulseDto pulse(LocalDate asOf, ShiftPeriod period) {
        EnumMap<AttendanceStatus, Long> counts = new EnumMap<>(AttendanceStatus.class);
        long total = 0;
        List<Object[]> rows = period == null
                ? records.countByStatusOnDate(asOf)
                : records.countByStatusOnDateAndShiftPeriod(asOf, period);
        for (Object[] row : rows) {
            AttendanceStatus s = (AttendanceStatus) row[0];
            long c = ((Number) row[1]).longValue();
            counts.put(s, c);
            total += c;
        }
        long present = counts.getOrDefault(AttendanceStatus.PRESENT, 0L);
        long halfDay = counts.getOrDefault(AttendanceStatus.HALF_DAY, 0L);
        // Late isn't a distinct status; it's a property of PRESENT rows.
        // Surfacing it here would need a second aggregate (PRESENT with
        // late_minutes > 0). Keep "late" at zero for v1 and add the second
        // aggregate when the FE asks for it — the HR dashboard's headline
        // numbers don't drive any action that depends on "late" alone.
        Long late = period == null
                ? records.countLateByDate(asOf)
                : records.countLateByDateAndShiftPeriod(asOf, period);
        log.info("late count we got: {} ", late);
        log.info("today's present count we got: {} ", present);
        return new OrgPulseDto(
                asOf,
                present,
                late != null ? late : 0L,
                counts.getOrDefault(AttendanceStatus.ABSENT, 0L),
                counts.getOrDefault(AttendanceStatus.LEAVE, 0L),
                halfDay,
                counts.getOrDefault(AttendanceStatus.HOLIDAY, 0L),
                counts.getOrDefault(AttendanceStatus.WEEKEND, 0L),
                total);
    }

    /** Employees whose record on {@code date} sits on a {@code period} shift. */
    @Transactional(readOnly = true)
    public List<Long> employeeIdsOnShiftPeriod(LocalDate date, ShiftPeriod period) {
        return records.findEmployeeIdsOnShiftPeriod(date, date, period);
    }

    /**
     * Per-day rollup over a (from, to) range, inclusive on both ends.
     * Days with no records are simply absent from the result — the FE
     * fills them as zero / weekend.
     */
    @Transactional(readOnly = true)
    public List<OrgAttendanceDayDto> dailyTrend(LocalDate from, LocalDate to) {
        Map<LocalDate, EnumMap<AttendanceStatus, Long>> byDay = new LinkedHashMap<>();
        for (Object[] row : records.countByDayStatus(from, to)) {
            LocalDate date = (LocalDate) row[0];
            AttendanceStatus s = (AttendanceStatus) row[1];
            long c = ((Number) row[2]).longValue();
            byDay.computeIfAbsent(date, d -> new EnumMap<>(AttendanceStatus.class)).put(s, c);
        }
        List<OrgAttendanceDayDto> out = new ArrayList<>(byDay.size());
        for (Map.Entry<LocalDate, EnumMap<AttendanceStatus, Long>> e : byDay.entrySet()) {
            EnumMap<AttendanceStatus, Long> m = e.getValue();
            long present = m.getOrDefault(AttendanceStatus.PRESENT, 0L);
            long absent = m.getOrDefault(AttendanceStatus.ABSENT, 0L);
            long onLeave = m.getOrDefault(AttendanceStatus.LEAVE, 0L);
            long halfDay = m.getOrDefault(AttendanceStatus.HALF_DAY, 0L);
            long total = m.values().stream().mapToLong(Long::longValue).sum();
            out.add(new OrgAttendanceDayDto(e.getKey(), present, 0L, absent, onLeave, halfDay, total));
        }
        return out;
    }
}
