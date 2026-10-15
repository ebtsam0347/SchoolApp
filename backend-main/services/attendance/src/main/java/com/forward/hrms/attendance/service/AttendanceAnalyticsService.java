package com.forward.hrms.attendance.service;

import com.forward.hrms.attendance.entity.AttendanceRecord;
import com.forward.hrms.attendance.enums.AttendanceStatus;
import com.forward.hrms.attendance.repository.AttendanceRecordRepository;

import lombok.RequiredArgsConstructor;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Attendance analytics — monthly on-time %, absenteeism %, OT-hours
 * trend, and an employee scorecard. All computed from
 * {@code attendance_records}; no parallel ledger needed.
 *
 * <p>Definitions:
 * <ul>
 *   <li>On-time % = PRESENT-with-late=0 / PRESENT (the "tried to come"
 *       denominator; weekends + holidays don't drag the score).</li>
 *   <li>Absenteeism % = ABSENT / (PRESENT + ABSENT + LEAVE).</li>
 *   <li>OT hours = sum of {@code (otNormal+otExtra+otSpecial)} minutes / 60.</li>
 * </ul>
 */
@Service
@RequiredArgsConstructor
public class AttendanceAnalyticsService {

    private static final DateTimeFormatter MONTH_FMT = DateTimeFormatter.ofPattern("yyyy-MM");
    private static final int DEFAULT_MONTHS = 12;
    private static final int MAX_MONTHS = 24;

    private final AttendanceRecordRepository records;

    @Transactional(readOnly = true)
    public OnTimeTrendDto onTimePct(int months) {
        return computeMonthlyAggregate(months, OnTimeTrendDto::new, AttendanceAnalyticsService::onTimeForMonth);
    }

    @Transactional(readOnly = true)
    public AbsenteeismTrendDto absenteeismPct(int months) {
        return computeMonthlyAggregate(months, AbsenteeismTrendDto::new, AttendanceAnalyticsService::absForMonth);
    }

    @Transactional(readOnly = true)
    public OtHoursTrendDto otHoursTrend(int months) {
        int n = clamp(months);
        YearMonth current = YearMonth.now();
        YearMonth start = current.minusMonths(n - 1L);
        Map<YearMonth, MonthlyBuckets> buckets = bucketsForRange(start, current);
        List<OtMonth> series = new ArrayList<>(n);
        for (int i = 0; i < n; i++) {
            YearMonth ym = start.plusMonths(i);
            MonthlyBuckets b = buckets.getOrDefault(ym, new MonthlyBuckets());
            BigDecimal hours = BigDecimal.valueOf(b.otMinutes)
                    .divide(BigDecimal.valueOf(60), 2, RoundingMode.HALF_UP);
            series.add(new OtMonth(ym.format(MONTH_FMT), b.otMinutes, hours));
        }
        return new OtHoursTrendDto(n, series);
    }

    @Transactional(readOnly = true)
    public ScorecardDto scorecard(long employeeId, int days) {
        int dayWindow = days <= 0 ? 90 : Math.min(days, 365);
        LocalDate to = LocalDate.now();
        LocalDate from = to.minusDays(dayWindow - 1L);

        List<AttendanceRecord> rows = records
                .findAllByEmployeeIdAndAttendanceDateBetweenOrderByAttendanceDateAsc(employeeId, from, to);
        long present = 0, ontime = 0, absent = 0, leave = 0, lateDays = 0;
        long otMinutes = 0;
        for (AttendanceRecord r : rows) {
            switch (r.getStatus()) {
                case PRESENT, HALF_DAY -> {
                    present++;
                    if (r.getLateMinutes() == 0) ontime++;
                    else lateDays++;
                }
                case ABSENT -> absent++;
                case LEAVE -> leave++;
                default -> {}
            }
            otMinutes += r.getOtNormalMinutes() + r.getOtExtraMinutes() + r.getOtSpecialMinutes();
        }
        BigDecimal onTimePct = present == 0 ? BigDecimal.ZERO
                : BigDecimal.valueOf(ontime).multiply(BigDecimal.valueOf(100))
                        .divide(BigDecimal.valueOf(present), 2, RoundingMode.HALF_UP);
        BigDecimal otHours = BigDecimal.valueOf(otMinutes).divide(BigDecimal.valueOf(60), 2, RoundingMode.HALF_UP);
        return new ScorecardDto(employeeId, from, to, dayWindow,
                present, ontime, lateDays, absent, leave, onTimePct, otHours);
    }

    // ---------- helpers ----------

    private interface MonthlyDtoFactory<T> {
        T create(int months, List<MonthlyEntry> series);
    }

    private interface MonthlyComputation {
        MonthlyEntry compute(YearMonth ym, MonthlyBuckets b);
    }

    private <T> T computeMonthlyAggregate(int months, MonthlyDtoFactory<T> factory, MonthlyComputation comp) {
        int n = clamp(months);
        YearMonth current = YearMonth.now();
        YearMonth start = current.minusMonths(n - 1L);
        Map<YearMonth, MonthlyBuckets> buckets = bucketsForRange(start, current);
        List<MonthlyEntry> series = new ArrayList<>(n);
        for (int i = 0; i < n; i++) {
            YearMonth ym = start.plusMonths(i);
            MonthlyBuckets b = buckets.getOrDefault(ym, new MonthlyBuckets());
            series.add(comp.compute(ym, b));
        }
        return factory.create(n, series);
    }

    private static MonthlyEntry onTimeForMonth(YearMonth ym, MonthlyBuckets b) {
        BigDecimal pct = b.present == 0 ? BigDecimal.ZERO
                : BigDecimal.valueOf(b.onTime).multiply(BigDecimal.valueOf(100))
                        .divide(BigDecimal.valueOf(b.present), 2, RoundingMode.HALF_UP);
        return new MonthlyEntry(ym.format(MONTH_FMT), b.present, b.onTime, pct);
    }

    private static MonthlyEntry absForMonth(YearMonth ym, MonthlyBuckets b) {
        long denominator = b.present + b.absent + b.leave;
        BigDecimal pct = denominator == 0 ? BigDecimal.ZERO
                : BigDecimal.valueOf(b.absent).multiply(BigDecimal.valueOf(100))
                        .divide(BigDecimal.valueOf(denominator), 2, RoundingMode.HALF_UP);
        return new MonthlyEntry(ym.format(MONTH_FMT), denominator, b.absent, pct);
    }

    private Map<YearMonth, MonthlyBuckets> bucketsForRange(YearMonth start, YearMonth end) {
        LocalDate from = start.atDay(1);
        LocalDate to = end.atEndOfMonth();
        Map<YearMonth, MonthlyBuckets> out = new HashMap<>();
        // Server-side group-by: ~months × statuses rows back (≤96 for a
        // 24-month window). The prior implementation hydrated up to 50k
        // AttendanceRecord entities per call and OOM'd the pod when two
        // dashboard endpoints fired concurrently.
        for (Object[] row : records.monthlyAggregate(from, to)) {
            int year = ((Number) row[0]).intValue();
            int month = ((Number) row[1]).intValue();
            AttendanceStatus status = (AttendanceStatus) row[2];
            long count = ((Number) row[3]).longValue();
            long onTime = ((Number) row[4]).longValue();
            long otMinutes = ((Number) row[5]).longValue();
            YearMonth ym = YearMonth.of(year, month);
            MonthlyBuckets b = out.computeIfAbsent(ym, k -> new MonthlyBuckets());
            switch (status) {
                case PRESENT, HALF_DAY -> {
                    b.present += count;
                    b.onTime += onTime;
                }
                case ABSENT -> b.absent += count;
                case LEAVE -> b.leave += count;
                default -> {}
            }
            b.otMinutes += otMinutes;
        }
        return out;
    }

    private static int clamp(int n) {
        if (n <= 0) return DEFAULT_MONTHS;
        return Math.min(n, MAX_MONTHS);
    }

    // ---------- DTOs / state ----------

    public record OnTimeTrendDto(int months, List<MonthlyEntry> series) {}
    public record AbsenteeismTrendDto(int months, List<MonthlyEntry> series) {}
    public record OtHoursTrendDto(int months, List<OtMonth> series) {}

    /**
     * Generic per-month numerator/denominator/pct so the on-time and
     * absenteeism series share a wire shape.
     */
    public record MonthlyEntry(String month, long denominator, long numerator, BigDecimal pct) {}
    public record OtMonth(String month, long otMinutes, BigDecimal otHours) {}

    public record ScorecardDto(
            long employeeId, LocalDate from, LocalDate to, int dayWindow,
            long presentDays, long onTimeDays, long lateDays, long absentDays, long leaveDays,
            BigDecimal onTimePct, BigDecimal otHours) {}

    private static final class MonthlyBuckets {
        long present, onTime, absent, leave;
        long otMinutes;
    }

    @SuppressWarnings("unused")
    private static AttendanceStatus[] terminalStatuses() {
        return new AttendanceStatus[]{AttendanceStatus.PRESENT, AttendanceStatus.ABSENT, AttendanceStatus.LEAVE};
    }
}
