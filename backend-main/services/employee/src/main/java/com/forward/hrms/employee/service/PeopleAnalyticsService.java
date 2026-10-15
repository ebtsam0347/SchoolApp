package com.forward.hrms.employee.service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.forward.hrms.employee.entity.Employee;
import com.forward.hrms.employee.entity.EmployeeExit;
import com.forward.hrms.employee.enums.EmploymentStatus;
import com.forward.hrms.employee.enums.ExitStatus;
import com.forward.hrms.employee.repository.EmployeeExitRepository;
import com.forward.hrms.employee.repository.EmployeeRepository;

import lombok.RequiredArgsConstructor;


@Service
@RequiredArgsConstructor
public class PeopleAnalyticsService {

    private static final DateTimeFormatter MONTH_FMT = DateTimeFormatter.ofPattern("yyyy-MM");
    private static final int DEFAULT_MONTHS = 12;
    private static final int MAX_MONTHS = 24;

    private final EmployeeRepository employees;
    private final EmployeeExitRepository exits;


    @Transactional(readOnly = true)
    public AttritionTrendDto attrition(int months) {
        int n = clamp(months);
        YearMonth current = YearMonth.now();
        YearMonth start = current.minusMonths(n - 1L);
        LocalDate from = start.atDay(1);
        LocalDate to = current.atEndOfMonth();

        Map<YearMonth, Long> leavers = bucket(exits.leaverCountsByMonth(from, to));

        // One headcount-as-of for the start anchor; walk forward applying
        // joiners/leavers deltas. Same shape as HeadcountTrendService.
        Map<YearMonth, Long> joiners = bucket(employees.joinerCountsByMonth(from, to));
        long running = employees.headcountAsOf(start.atDay(1).minusDays(1));

        List<AttritionMonth> series = new ArrayList<>(n);
        long rolling12LeaverSum = 0;
        long rolling12HcSum = 0;
        for (int i = 0; i < n; i++) {
            YearMonth ym = start.plusMonths(i);
            long startHc = running;
            long j = joiners.getOrDefault(ym, 0L);
            long l = leavers.getOrDefault(ym, 0L);
            running += j - l;
            long endHc = running;
            BigDecimal avgHc = avg(startHc, endHc);
            BigDecimal rate = pctRate(l, avgHc);
            series.add(new AttritionMonth(ym.format(MONTH_FMT), l, startHc, endHc, rate));

            // Rolling 12m window — last 12 months including current.
            if (i >= n - 12) {
                rolling12LeaverSum += l;
                rolling12HcSum += avgHc.longValue();
            }
        }
        BigDecimal rolling12 = rolling12HcSum == 0
                ? BigDecimal.ZERO
                : BigDecimal.valueOf(rolling12LeaverSum)
                        .multiply(BigDecimal.valueOf(100))
                        .divide(BigDecimal.valueOf(rolling12HcSum / 12.0), 2, RoundingMode.HALF_UP);
        return new AttritionTrendDto(n, series, rolling12);
    }

    /**
     * Snapshot of active employees bucketed into the five tenure bands.
     * Optional {@code splitBy=dept|grade} returns a per-bucket breakdown.
     */
    @Transactional(readOnly = true)
    public TenureBandsDto tenureBands(String splitBy) {
        LocalDate today = LocalDate.now();
        List<Employee> active = employees.findAll().stream()
                .filter(e -> e.getEmploymentStatus() != EmploymentStatus.TERMINATED)
                .filter(e -> e.getJoiningDate() != null)
                .toList();

        Map<String, long[]> byKey = new TreeMap<>();
        long[] totals = new long[5];
        for (Employee e : active) {
            int idx = bandIndex(years(e.getJoiningDate(), today));
            totals[idx]++;
            String key = splitKey(splitBy, e);
            byKey.computeIfAbsent(key, k -> new long[5])[idx]++;
        }

        List<TenureBandSplit> splits = new ArrayList<>();
        for (Map.Entry<String, long[]> e : byKey.entrySet()) {
            splits.add(new TenureBandSplit(e.getKey(), bandsArray(e.getValue())));
        }
        return new TenureBandsDto(active.size(), bandsArray(totals), splitBy, splits);
    }

    private static String splitKey(String splitBy, Employee e) {
        if (splitBy == null) return "all";
        return switch (splitBy) {
            case "dept", "department" ->
                    e.getDepartmentId() == null ? "(unassigned)" : "dept:" + e.getDepartmentId();
            case "grade" ->
                    e.getGradeId() == null ? "(unassigned)" : "grade:" + e.getGradeId();
            default -> "all";
        };
    }

    private static int bandIndex(double y) {
        if (y < 1)  return 0;
        if (y < 3)  return 1;
        if (y < 5)  return 2;
        if (y < 10) return 3;
        return 4;
    }

    private static double years(LocalDate from, LocalDate to) {
        return ChronoUnit.DAYS.between(from, to) / 365.25;
    }

    private static long[] bandsArray(long[] in) {
        return new long[]{in[0], in[1], in[2], in[3], in[4]};
    }

    private static BigDecimal avg(long a, long b) {
        return BigDecimal.valueOf(a + b).divide(BigDecimal.valueOf(2), 2, RoundingMode.HALF_UP);
    }

    private static BigDecimal pctRate(long leavers, BigDecimal avgHc) {
        if (avgHc.signum() == 0) return BigDecimal.ZERO;
        return BigDecimal.valueOf(leavers)
                .multiply(BigDecimal.valueOf(100))
                .divide(avgHc, 2, RoundingMode.HALF_UP);
    }

    private static Map<YearMonth, Long> bucket(List<Object[]> rows) {
        Map<YearMonth, Long> out = new HashMap<>();
        for (Object[] r : rows) {
            int year = ((Number) r[0]).intValue();
            int month = ((Number) r[1]).intValue();
            long count = ((Number) r[2]).longValue();
            out.put(YearMonth.of(year, month), count);
        }
        return out;
    }

    private static int clamp(int n) {
        if (n <= 0) return DEFAULT_MONTHS;
        return Math.min(n, MAX_MONTHS);
    }

    public record AttritionTrendDto(int months, List<AttritionMonth> series, BigDecimal rollingTwelveMonthPct) {}
    public record AttritionMonth(String month, long leavers, long startHeadcount, long endHeadcount, BigDecimal ratePct) {}

    public record TenureBandsDto(long total, TenureBandCounts overall, String splitBy, List<TenureBandSplit> splits) {
        public TenureBandsDto(long total, long[] overall, String splitBy, List<TenureBandSplit> splits) {
            this(total, TenureBandCounts.of(overall), splitBy, splits);
        }
    }
    public record TenureBandSplit(String key, TenureBandCounts counts) {
        public TenureBandSplit(String key, long[] counts) {
            this(key, TenureBandCounts.of(counts));
        }
    }
    public record TenureBandCounts(long under1y, long y1to3, long y3to5, long y5to10, long y10plus) {
        public static TenureBandCounts of(long[] a) {
            return new TenureBandCounts(a[0], a[1], a[2], a[3], a[4]);
        }
    }

    @SuppressWarnings("unused")
    private static List<EmployeeExit> filterCompleted(List<EmployeeExit> all) {
        return all.stream().filter(x -> x.getStatus() == ExitStatus.COMPLETED).toList();
    }
}
