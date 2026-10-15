package com.forward.hrms.attendance.service;

import com.forward.hrms.attendance.dto.response.*;
import com.forward.hrms.attendance.entity.AttendanceRecord;
import com.forward.hrms.attendance.repository.AttendancePunchRepository;
import com.forward.hrms.attendance.repository.AttendanceRecordRepository;
import com.forward.hrms.attendance.repository.DeviceEventLogRepository;
import com.forward.hrms.attendance.repository.OvertimeRequestRepository;
import com.forward.hrms.attendance.repository.projection.ApprovedOvertimeProjection;
import com.forward.hrms.attendance.repository.projection.PendingOvertimeProjection;
import com.forward.hrms.attendance.repository.projection.TerminationAttendanceAggregate;
import com.forward.hrms.attendance.repository.projection.UnappliedOvertimeProjection;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.util.*;

@Service
@RequiredArgsConstructor
public class AttendanceQueryService {

    private final AttendanceRecordRepository records;
    private final OvertimeRequestRepository overtimeRequestsRepository;
    private final AttendancePunchRepository punches;
    private final DeviceEventLogRepository deviceEvents;
    private final com.forward.hrms.attendance.client.EmployeeClient employeeClient;
    private final com.forward.hrms.commons.cache.EmployeeDisplayCache displayCache;

    @Transactional(readOnly = true)
    public List<AttendanceRecordDto> month(long employeeId, YearMonth month) {
        LocalDate from = month.atDay(1);
        LocalDate to = month.atEndOfMonth();
        return records.findAllByEmployeeIdAndAttendanceDateBetweenOrderByAttendanceDateAsc(employeeId, from, to)
                .stream().map(AttendanceRecordDtoMapper::of).toList();
    }

    @Transactional(readOnly = true)
    public Optional<AttendanceRecordDto> day(long employeeId, LocalDate date) {
        return records.findByEmployeeIdAndAttendanceDate(employeeId, date).map(AttendanceRecordDtoMapper::of);
    }

    /**
     * Batch single-date lookup. Returns a map keyed by employeeId so a
     * caller iterating direct reports can do {@code rows.get(emp.id())}
     * without another loop. Missing rows = no record for that day (caller
     * decides how to render).
     */
    @Transactional(readOnly = true)
    public Map<Long, AttendanceRecordDto> dayBatch(java.util.Collection<Long> employeeIds, LocalDate date) {
        if (employeeIds == null || employeeIds.isEmpty()) return Map.of();
        Map<Long, AttendanceRecordDto> out = new HashMap<>();
        for (AttendanceRecord r : records.findAllByEmployeeIdInAndAttendanceDate(employeeIds, date)) {
            out.put(r.getEmployeeId(), AttendanceRecordDtoMapper.of(r));
        }
        return out;
    }

    /**
     * One page of the caller's own department on {@code date} — same row
     * shape as the BFF's manager {@code team/today}, {@code PENDING_PUNCH}
     * where no record exists.
     *
     * <p>Membership and the {@code sectionId} / {@code search} filters come
     * from employee-service's caller-scoped department list (so the caller
     * needs no {@code attendance.admin} or manager coverage). Status lives
     * here, not there, so the whole filtered department is read (one
     * upstream call per 200 people, one attendance query), narrowed by
     * {@code status}, and paged in memory — order stays by employee code.
     * Summaries are resolved for the returned page only; an id the display
     * cache can't resolve (deleted mid-request) is left off the page.
     *
     * <p>Deliberately not {@code @Transactional}: the upstream HTTP calls run
     * first, and a transaction would hold a pooled connection idle through them.
     */
    public com.forward.hrms.commons.web.page.PageResponse<DepartmentAttendanceRow> departmentDay(
            LocalDate date, Long sectionId, String search,
            com.forward.hrms.attendance.enums.AttendanceStatus status,
            org.springframework.data.domain.Pageable pageable) {
        List<Long> ids = employeeClient.myDepartmentEmployees(sectionId, search).stream()
                .map(com.forward.hrms.attendance.client.EmployeeClient.EmployeeView::id).toList();
        Map<Long, AttendanceRecordDto> byId = dayBatch(ids, date);

        // PENDING_PUNCH (no record) isn't an AttendanceStatus, so a status
        // filter only ever matches employees who have a record that day.
        List<Long> matching = status == null ? ids : ids.stream()
                .filter(id -> byId.get(id) != null && status.name().equals(byId.get(id).status()))
                .toList();

        int from = (int) Math.min(pageable.getOffset(), matching.size());
        int to = Math.min(from + pageable.getPageSize(), matching.size());
        List<Long> pageIds = matching.subList(from, to);

        var displays = displayCache.getMany(pageIds);
        List<DepartmentAttendanceRow> rows = new ArrayList<>(pageIds.size());
        for (Long id : pageIds) {
            var d = displays.get(id);
            if (d == null) continue;
            AttendanceRecordDto rec = byId.get(id);
            rows.add(new DepartmentAttendanceRow(
                    com.forward.hrms.commons.cache.EmployeeSummaryMapper.toSummary(d),
                    statusOf(rec),
                    rec == null ? null : rec.checkIn(),
                    rec == null ? null : rec.checkOut(),
                    rec == null ? 0 : rec.lateMinutes()));
        }
        var page = new org.springframework.data.domain.PageImpl<>(
                pageIds, pageable, matching.size());
        return com.forward.hrms.commons.web.page.PageResponse.from(page, rows);
    }

    /** Roll-call status: the record's, or {@code PENDING_PUNCH} without one. */
    private static String statusOf(AttendanceRecordDto rec) {
        return rec == null ? "PENDING_PUNCH" : rec.status();
    }

    /**
     * Per-employee aggregated payroll snapshot. Mirrors the per-day
     * aggregation payroll's {@code AttendanceClient.snapshot} computes
     * today, but in one round-trip across many employees. Caller bucks
     * by {@code employeeId}. Missing employees return a zero snapshot
     * (matches the existing per-employee semantics).
     */
    @Transactional(readOnly = true)
    public Map<Long, com.forward.hrms.attendance.dto.response.AttendanceSnapshotDto> snapshots(
            java.util.Collection<Long> employeeIds, LocalDate from, LocalDate to) {
        Map<Long, com.forward.hrms.attendance.dto.response.AttendanceSnapshotDto> out = new HashMap<>();
        if (employeeIds == null || employeeIds.isEmpty()) return out;
        int daysInPeriod = (int) java.time.temporal.ChronoUnit.DAYS.between(from, to) + 1;
        Map<Long, TallyBuilder> tally = new HashMap<>();
        for (Long id : employeeIds) tally.put(id, new TallyBuilder());
        for (AttendanceRecord r : records
                .findAllByEmployeeIdInAndAttendanceDateBetweenOrderByEmployeeIdAscAttendanceDateAsc(
                        employeeIds, from, to)) {
            TallyBuilder b = tally.get(r.getEmployeeId());
            if (b == null) continue;
            b.add(r);
        }
        for (var entry : tally.entrySet()) {
            Tally t = entry.getValue().build();
            java.math.BigDecimal lopDays = java.math.BigDecimal.valueOf(t.absent());
            java.math.BigDecimal otHours = java.math.BigDecimal.valueOf(t.otMinutes())
                    .divide(java.math.BigDecimal.valueOf(60), 4, java.math.RoundingMode.CEILING);
            int daysPresent = t.present() + (t.halfDay() / 2);
            if (t.halfDay() % 2 == 1) daysPresent++;
            int workingDays = Math.max(0, daysInPeriod - t.holiday() - t.sunday());
            java.math.BigDecimal absentDays = java.math.BigDecimal.valueOf(t.absent())
                    .add(java.math.BigDecimal.valueOf(t.halfDay()).divide(java.math.BigDecimal.valueOf(2)));
            java.math.BigDecimal paidDays = java.math.BigDecimal.valueOf(workingDays).subtract(absentDays);
            if (paidDays.signum() < 0) paidDays = java.math.BigDecimal.ZERO;
            out.put(entry.getKey(), new com.forward.hrms.attendance.dto.response.AttendanceSnapshotDto(
                    lopDays, daysPresent, otHours, workingDays, paidDays, absentDays, t.holiday(), t.sunday()));
        }
        return out;
    }


    // new method to get the attendance & overtime summary of information required
    // for termination process.
    @Transactional(readOnly = true)
    public TerminationAttendanceSummaryDto terminationSummary(
            long employeeId,
            LocalDate from,
            LocalDate to

    ) {

        TerminationAttendanceAggregate attendance = records.getTerminationAttendanceSummary(
                employeeId,
                from,
                to
        );

        PendingOvertimeProjection pending = overtimeRequestsRepository.getPendingOvertimeSummary(
                employeeId,
                from,
                to
        );

        UnappliedOvertimeProjection unapplied = overtimeRequestsRepository.getUnappliedOvertimeSummary(
                employeeId,
                from,
                to
        );

        ApprovedOvertimeProjection approved = overtimeRequestsRepository.getApprovedOvertimeSummary(
                employeeId,
                from,
                to
        );

        return new TerminationAttendanceSummaryDto(

                employeeId,

                from,

                to,

                buildAttendanceSummary(attendance),

                buildTerminationOvertimeSummary(approved, pending, unapplied)
        );
    }

    // Normal-overtime-only snapshot for termination settlement. Extra and
    // Special overtime are deliberately excluded here — they remain fully
    // available on the general overtime endpoints, unaffected by this.
    private NormalOvertimeSummaryDto buildTerminationOvertimeSummary(
            ApprovedOvertimeProjection approved,
            PendingOvertimeProjection pending,
            UnappliedOvertimeProjection unapplied
    ) {

        DurationDto approvedNormal = toDuration(approved == null ? 0 : defaultInt(approved.getNormalMinutes()));
        DurationDto pendingNormal = toDuration(pending == null ? 0 : defaultInt(pending.getNormalMinutes()));
        DurationDto unappliedNormal = toDuration(unapplied == null ? 0 : defaultInt(unapplied.getNormalMinutes()));

        int totalMinutes = approvedNormal.minutes() + pendingNormal.minutes() + unappliedNormal.minutes();

        return new NormalOvertimeSummaryDto(
                approvedNormal,
                pendingNormal,
                unappliedNormal,
                toDuration(totalMinutes)
        );
    }

    private AttendanceSummaryDto buildAttendanceSummary(TerminationAttendanceAggregate a) {

        if (a == null) {
            return new AttendanceSummaryDto(
                    0,
                    0,
                    0,
                    0,
                    0,
                    0,
                    0,
                    0
            );
        }

        return new AttendanceSummaryDto(

                defaultLong(a.getPresentDays()),

                defaultLong(a.getHalfDays()),

                defaultLong(a.getLeaveDays()),

                defaultLong(a.getPaidLeaveDays()),

                defaultLong(a.getUnpaidLeaveDays()),

                defaultLong(a.getAbsentDays()),

                defaultLong(a.getWeekendDays()),

                defaultLong(a.getHolidayDays())
        );
    }

    private DurationDto toDuration(int minutes) {

        return new DurationDto(

                minutes,

                formatDuration(minutes)
        );
    }

    // I recommend not using decimal hours (7.50) because HR users usually understand "7h 30m" more easily.
    private String formatDuration(int minutes) {

        int hours = minutes / 60;

        int remainingMinutes = minutes % 60;

        return hours + "h " + remainingMinutes + "m";
    }

    private long defaultLong(Long value) {

        return value == null ? 0L : value;
    }

    private int defaultInt(Integer value) {

        return value == null ? 0 : value;
    }


    private record Tally(int present, int halfDay, int absent, int holiday, int sunday, int otMinutes) {
    }

    private static final class TallyBuilder {
        int present, halfDay, absent, holiday, sunday, otMinutes;

        void add(AttendanceRecord r) {
            switch (r.getStatus()) {
                case PRESENT -> present++;
                case HALF_DAY -> halfDay++;
                case ABSENT -> absent++;
                case HOLIDAY -> holiday++;
                case WEEKEND -> sunday++;
                default -> { /* LEAVE intentionally ignored */ }
            }
            otMinutes += r.getOtNormalMinutes() + r.getOtExtraMinutes() + r.getOtSpecialMinutes();
        }

        Tally build() {
            return new Tally(present, halfDay, absent, holiday, sunday, otMinutes);
        }
    }

    @Transactional(readOnly = true)
    public List<AttendanceRecordDto> range(long employeeId, LocalDate from, LocalDate to) {
        return records.findAllByEmployeeIdAndAttendanceDateBetweenOrderByAttendanceDateAsc(employeeId, from, to)
                .stream().map(AttendanceRecordDtoMapper::of).toList();
    }

    @Transactional(readOnly = true)
    public List<PunchDto> punchesOn(long employeeId, LocalDate date) {
        return punches.findAllByEmployeeIdAndAttendanceDateOrderByPunchedAtAsc(employeeId, date)
                .stream().map(PunchDtoMapper::of).toList();
    }

    @Transactional(readOnly = true)
    public List<PunchDto> punchesRange(long employeeId, LocalDate from, LocalDate to) {
        return punches.findAllByEmployeeIdAndAttendanceDateBetweenOrderByPunchedAtAsc(employeeId, from, to)
                .stream().map(PunchDtoMapper::of).toList();
    }

    /**
     * Aggregates canteen device events for an employee over a date range.
     * Used by payroll's compute to apply the grade-conditional canteen
     * deduction (management → weeks-with-attendance × week-rate;
     * supervisor / labour → days × per-day rate). Weeks are 7-day chunks
     * counted from the 1st of the month, not ISO calendar weeks; days 1–7
     * is week 0, 8–14 is week 1, and so on.
     */
    @Transactional(readOnly = true)
    public CanteenUsageDto canteenUsage(long employeeId, LocalDate from, LocalDate to) {
        java.time.LocalDateTime fromTs = from.atStartOfDay();
        java.time.LocalDateTime toExclusive = to.plusDays(1).atStartOfDay();
        List<LocalDate> dates = deviceEvents.findDistinctCanteenDates(employeeId, fromTs, toExclusive);
        int days = dates.size();
        int weeks = (int) dates.stream()
                .mapToInt(d -> (d.getDayOfMonth() - 1) / 7)
                .distinct()
                .count();
        return new CanteenUsageDto(days, weeks);
    }

    /**
     * Per-day heatmap rollup for {@code (employee, month)}. Days with no
     * record fall back to WEEKEND on Sunday and ABSENT on weekdays — this
     * matches the convention the BFF dashboard composite uses, and means
     * the FE doesn't have to know the working-week shape.
     *
     * <p>{@code varianceMin} is the day's {@code late_minutes} for PRESENT
     * rows, zero otherwise. Server-side computation (D-S5 in BFF_WISHLIST.md)
     * keeps render cost flat as employees accumulate punches.
     */
    @Transactional(readOnly = true)
    public EmployeeHeatmapDto heatmap(long employeeId, YearMonth month) {
        LocalDate from = month.atDay(1);
        LocalDate to = month.atEndOfMonth();
        List<AttendanceRecord> rows = records
                .findAllByEmployeeIdAndAttendanceDateBetweenOrderByAttendanceDateAsc(employeeId, from, to);
        Map<LocalDate, AttendanceRecord> byDate = new HashMap<>();
        for (AttendanceRecord r : rows) byDate.put(r.getAttendanceDate(), r);

        List<EmployeeHeatmapDto.Day> days = new ArrayList<>(month.lengthOfMonth());
        for (LocalDate d = from; !d.isAfter(to); d = d.plusDays(1)) {
            AttendanceRecord rec = byDate.get(d);
            String status;
            int variance = 0;
            if (rec != null) {
                // Surface LATE separately from PRESENT for the heatmap so
                // late days render distinctly even though late_minutes
                // alone could carry that signal — the FE colour scheme
                // wants a status string, not a threshold.
                if (rec.getStatus() == com.forward.hrms.attendance.enums.AttendanceStatus.PRESENT
                        && rec.getLateMinutes() > 0) {
                    status = "LATE";
                } else {
                    status = rec.getStatus().name();
                }
                variance = rec.getLateMinutes();
            } else {
                status = d.getDayOfWeek() == DayOfWeek.SUNDAY ? "WEEKEND" : "ABSENT";
            }
            days.add(new EmployeeHeatmapDto.Day(d, status, variance));
        }
        return new EmployeeHeatmapDto(employeeId,
                month.format(DateTimeFormatter.ofPattern("yyyy-MM")),
                days);
    }
}
