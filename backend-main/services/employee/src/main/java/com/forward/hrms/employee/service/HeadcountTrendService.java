package com.forward.hrms.employee.service;

import java.time.LocalDate;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.forward.hrms.employee.dto.response.HeadcountTrendDto;
import com.forward.hrms.employee.repository.EmployeeExitRepository;
import com.forward.hrms.employee.repository.EmployeeRepository;

import lombok.RequiredArgsConstructor;


@Service
@RequiredArgsConstructor
public class HeadcountTrendService {

    private static final DateTimeFormatter MONTH_FMT = DateTimeFormatter.ofPattern("yyyy-MM");
    private static final int MIN_MONTHS = 1;
    private static final int MAX_MONTHS = 24;

    private final EmployeeRepository employees;
    private final EmployeeExitRepository exits;

    @Transactional(readOnly = true)
    public HeadcountTrendDto trend(int months) {
        int n = clamp(months);
        YearMonth current = YearMonth.now();
        YearMonth start = current.minusMonths(n - 1L);
        LocalDate from = start.atDay(1);
        LocalDate to = current.atEndOfMonth();

        Map<YearMonth, Long> joiners = bucket(employees.joinerCountsByMonth(from, to));
        Map<YearMonth, Long> leavers = bucket(exits.leaverCountsByMonth(from, to));

        // End of (start - 1 month) anchor — walking forward sums the deltas
        // each month. One headcountAsOf query keeps total reads to three.
        long running = employees.headcountAsOf(start.atDay(1).minusDays(1));
        List<HeadcountTrendDto.Month> series = new ArrayList<>(n);
        YearMonth ym = start;
        for (int i = 0; i < n; i++) {
            long j = joiners.getOrDefault(ym, 0L);
            long l = leavers.getOrDefault(ym, 0L);
            running += j - l;
            series.add(new HeadcountTrendDto.Month(ym.format(MONTH_FMT), j, l, running));
            ym = ym.plusMonths(1);
        }
        return new HeadcountTrendDto(n, series);
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
        if (n < MIN_MONTHS) return MIN_MONTHS;
        if (n > MAX_MONTHS) return MAX_MONTHS;
        return n;
    }
}
