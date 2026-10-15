package com.forward.hrms.attendance.service;

import com.forward.hrms.attendance.entity.LeaveBalance;
import com.forward.hrms.attendance.entity.LeaveRequest;
import com.forward.hrms.attendance.entity.LeaveType;
import com.forward.hrms.attendance.enums.LeaveRequestStatus;
import com.forward.hrms.attendance.repository.LeaveBalanceRepository;
import com.forward.hrms.attendance.repository.LeaveRequestRepository;
import com.forward.hrms.attendance.repository.LeaveTypeRepository;

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
import java.util.TreeMap;

/**
 * Leave analytics — utilization (used / entitled) per leave type for a
 * cycle, and seasonality (days taken per month). Both reads from
 * {@code leave_balances} and {@code leave_requests}.
 */
@Service
@RequiredArgsConstructor
public class LeaveAnalyticsService {

    private static final DateTimeFormatter MONTH_FMT = DateTimeFormatter.ofPattern("yyyy-MM");
    private static final int DEFAULT_MONTHS = 12;
    private static final int MAX_MONTHS = 24;

    private final LeaveBalanceRepository balances;
    private final LeaveRequestRepository requests;
    private final LeaveTypeRepository leaveTypes;

    @Transactional(readOnly = true)
    public UtilizationDto utilization(int year) {
        Map<Long, LeaveType> typesById = typesById();
        Map<Long, BigDecimal[]> totals = new HashMap<>();   // [allocated, used, pending, carried]
        for (LeaveBalance b : balances.findAll()) {
            if (b.getYear() != year) continue;
            BigDecimal[] arr = totals.computeIfAbsent(b.getLeaveTypeId(),
                    k -> new BigDecimal[]{BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO});
            arr[0] = arr[0].add(b.getAllocated());
            arr[1] = arr[1].add(b.getUsed());
            arr[2] = arr[2].add(b.getPending());
            arr[3] = arr[3].add(b.getCarried());
        }
        List<UtilizationByType> rows = new ArrayList<>();
        BigDecimal totalAlloc = BigDecimal.ZERO;
        BigDecimal totalUsed = BigDecimal.ZERO;
        for (Map.Entry<Long, BigDecimal[]> e : totals.entrySet()) {
            LeaveType type = typesById.get(e.getKey());
            BigDecimal alloc = e.getValue()[0].add(e.getValue()[3]); // include carried
            BigDecimal used = e.getValue()[1];
            BigDecimal pending = e.getValue()[2];
            BigDecimal pct = alloc.signum() == 0 ? BigDecimal.ZERO
                    : used.multiply(BigDecimal.valueOf(100))
                            .divide(alloc, 2, RoundingMode.HALF_UP);
            rows.add(new UtilizationByType(
                    e.getKey(),
                    type == null ? "?" : type.getCode(),
                    type == null ? "?" : type.getName(),
                    alloc, used, pending, pct));
            totalAlloc = totalAlloc.add(alloc);
            totalUsed = totalUsed.add(used);
        }
        BigDecimal overallPct = totalAlloc.signum() == 0 ? BigDecimal.ZERO
                : totalUsed.multiply(BigDecimal.valueOf(100))
                        .divide(totalAlloc, 2, RoundingMode.HALF_UP);
        rows.sort((a, b) -> a.code().compareTo(b.code()));
        return new UtilizationDto(year, totalAlloc, totalUsed, overallPct, rows);
    }

    @Transactional(readOnly = true)
    public SeasonalityDto seasonality(int months) {
        int n = clamp(months);
        YearMonth current = YearMonth.now();
        YearMonth start = current.minusMonths(n - 1L);
        LocalDate from = start.atDay(1);
        LocalDate to = current.atEndOfMonth();

        Map<Long, LeaveType> typesById = typesById();
        // [year-month][leaveTypeId] = days
        Map<YearMonth, Map<Long, BigDecimal>> table = new TreeMap<>();
        for (LeaveRequest r : requests.findAll()) {
            if (r.getStatus() != LeaveRequestStatus.APPROVED
                    && r.getStatus() != LeaveRequestStatus.PARTIALLY_APPROVED) continue;
            if (r.getFromDate() == null || r.getToDate() == null) continue;
            if (r.getToDate().isBefore(from) || r.getFromDate().isAfter(to)) continue;
            // Apportion by month: walk each day in the request, attribute
            // 1 (or 0.5 for half day) to the month it falls in.
            BigDecimal perDay = r.isHalfDay() ? new BigDecimal("0.5") : BigDecimal.ONE;
            LocalDate d = r.getFromDate().isBefore(from) ? from : r.getFromDate();
            LocalDate end = r.getToDate().isAfter(to) ? to : r.getToDate();
            while (!d.isAfter(end)) {
                YearMonth ym = YearMonth.from(d);
                Map<Long, BigDecimal> byType = table.computeIfAbsent(ym, k -> new HashMap<>());
                byType.merge(r.getLeaveTypeId(), perDay, BigDecimal::add);
                d = d.plusDays(1);
            }
        }
        List<SeasonalityMonth> series = new ArrayList<>(n);
        for (int i = 0; i < n; i++) {
            YearMonth ym = start.plusMonths(i);
            Map<Long, BigDecimal> byType = table.getOrDefault(ym, Map.of());
            BigDecimal total = byType.values().stream().reduce(BigDecimal.ZERO, BigDecimal::add);
            List<SeasonalityByType> perType = byType.entrySet().stream()
                    .sorted((a, b) -> a.getKey().compareTo(b.getKey()))
                    .map(e -> {
                        LeaveType t = typesById.get(e.getKey());
                        return new SeasonalityByType(
                                e.getKey(),
                                t == null ? "?" : t.getCode(),
                                e.getValue());
                    })
                    .toList();
            series.add(new SeasonalityMonth(ym.format(MONTH_FMT), total, perType));
        }
        return new SeasonalityDto(n, series);
    }

    private Map<Long, LeaveType> typesById() {
        Map<Long, LeaveType> out = new HashMap<>();
        for (LeaveType t : leaveTypes.findAll()) out.put(t.getId(), t);
        return out;
    }

    private static int clamp(int n) {
        if (n <= 0) return DEFAULT_MONTHS;
        return Math.min(n, MAX_MONTHS);
    }

    public record UtilizationDto(
            int year, BigDecimal totalAllocated, BigDecimal totalUsed, BigDecimal overallPct,
            List<UtilizationByType> byLeaveType) {}
    public record UtilizationByType(
            long leaveTypeId, String code, String name,
            BigDecimal allocated, BigDecimal used, BigDecimal pending, BigDecimal usedPct) {}

    public record SeasonalityDto(int months, List<SeasonalityMonth> series) {}
    public record SeasonalityMonth(String month, BigDecimal totalDays, List<SeasonalityByType> byLeaveType) {}
    public record SeasonalityByType(long leaveTypeId, String code, BigDecimal days) {}
}
