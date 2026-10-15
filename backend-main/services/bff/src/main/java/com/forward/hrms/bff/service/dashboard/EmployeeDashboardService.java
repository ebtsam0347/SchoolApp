package com.forward.hrms.bff.service.dashboard;

import com.forward.hrms.bff.client.AttendanceClient;
import com.forward.hrms.bff.client.EmployeeClient;
import com.forward.hrms.bff.client.PayrollClient;
import com.forward.hrms.employee.dto.response.EmployeeDto;
import com.forward.hrms.bff.client.dto.PageView;
import com.forward.hrms.bff.dto.response.dashboard.EmployeeDashboard;
import com.forward.hrms.bff.dto.response.dashboard.Money;
import com.forward.hrms.attendance.dto.response.AttendanceRecordDto;
import com.forward.hrms.attendance.dto.response.LeaveBalanceDto;
import com.forward.hrms.attendance.dto.response.LeaveRequestDto;
import com.forward.hrms.attendance.dto.response.LeaveTypeDto;
import com.forward.hrms.attendance.dto.response.HolidayDto;
import com.forward.hrms.attendance.dto.response.OvertimeRequestDto;
import com.forward.hrms.attendance.dto.response.EmployeeHeatmapDto;
import com.forward.hrms.attendance.dto.response.ShiftDto;
import com.forward.hrms.payroll.dto.response.PayslipDto;
import com.forward.hrms.payroll.dto.response.PayrollRunDto;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;

/**
 * Composite for {@code GET /bff/dashboard/employee} — replaces ~6 fan-outs
 * the Employee landing would otherwise need (employee me, payslips,
 * leave balance, attendance month, holidays, notifications). All slices
 * fan out in parallel on the shared bff virtual-thread executor.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class EmployeeDashboardService {

    private static final int RECENT_PAYSLIPS = 3;
    private static final int UPCOMING_HOLIDAYS = 3;
    private static final int NEEDS_YOU_TOP = 5;
    private static final DateTimeFormatter MONTH_FMT = DateTimeFormatter.ofPattern("yyyy-MM");

    private final EmployeeClient employees;
    private final AttendanceClient attendance;
    private final PayrollClient payroll;
    private final DashboardSupport support;

    public EmployeeDashboard build() {
        EmployeeDto me = employees.me();

        LocalDate today = LocalDate.now();
        YearMonth currentMonth = YearMonth.from(today);
        String monthArg = currentMonth.format(MONTH_FMT);
        int year = today.getYear();
        int prevYear = year - 1;

        // Every slice below is soft — if the caller lacks the relevant
        // scope (or the slice 404s) we surface an empty section rather
        // than failing the whole composite. 5xx still propagates.
        PageView<PayslipDto> emptyPayslips =
                new PageView<>(List.of(), 0, 12, 0L, 0, null);
        EmployeeHeatmapDto emptyHeatmap =
                new EmployeeHeatmapDto(me.id(), monthArg, List.of());

        CompletableFuture<List<AttendanceRecordDto>> monthF =
                support.supplyOrDefault(() -> attendance.myMonth(monthArg), List.of());
        CompletableFuture<EmployeeHeatmapDto> heatmapF =
                support.supplyOrDefault(() -> attendance.myHeatmap(monthArg), emptyHeatmap);
        CompletableFuture<List<LeaveBalanceDto>> balancesF =
                support.supplyOrDefault(attendance::myBalances, List.of());
        CompletableFuture<List<LeaveTypeDto>> typesF =
                support.supplyOrDefault(attendance::leaveTypes, List.of());
        CompletableFuture<List<LeaveRequestDto>> approvedLeavesF =
                support.supplyOrDefault(() -> attendance.myLeaves("APPROVED"), List.of());
        CompletableFuture<List<LeaveRequestDto>> pendingLeavesF =
                support.supplyOrDefault(() -> attendance.myLeaves("PENDING"), List.of());
        CompletableFuture<List<OvertimeRequestDto>> myOtF =
                support.supplyOrDefault(attendance::myOvertime, List.of());
        CompletableFuture<List<HolidayDto>> holidaysF =
                support.supplyOrDefault(() -> attendance.holidays(year), List.of());
        CompletableFuture<PageView<PayslipDto>> currentYearPayslipsF =
                support.supplyOrDefault(() -> payroll.payslipsForEmployeeYear(me.id(), year, 0, 12),
                        emptyPayslips);
        CompletableFuture<PageView<PayslipDto>> prevYearPayslipsF =
                support.supplyOrDefault(() -> payroll.payslipsForEmployeeYear(me.id(), prevYear, 0, 12),
                        emptyPayslips);
        CompletableFuture<List<ShiftDto>> shiftsF =
                support.supplyOrDefault(attendance::shifts, List.of());

        try {
            CompletableFuture.allOf(monthF, heatmapF, balancesF, typesF, approvedLeavesF, pendingLeavesF,
                    myOtF, holidaysF, currentYearPayslipsF, prevYearPayslipsF, shiftsF).join();

            List<AttendanceRecordDto> monthRecs = monthF.get();
            AttendanceRecordDto todayRec = monthRecs.stream()
                    .filter(r -> today.equals(r.workDate()))
                    .findFirst().orElse(null);
            LeaveRequestDto activeLeave = approvedLeavesF.get().stream()
                    .filter(l -> !today.isBefore(l.fromDate()) && !today.isAfter(l.toDate()))
                    .findFirst().orElse(null);
            HolidayDto todayHoliday = holidaysF.get().stream()
                    .filter(h -> today.equals(h.holidayDate()))
                    .findFirst().orElse(null);

            Map<Long, String> shiftNames = new HashMap<>();
            for (ShiftDto s : shiftsF.get()) shiftNames.put(s.id(), s.name());

            EmployeeDashboard.Today today1 = EmployeeDashboard.derive(
                    today, todayRec, activeLeave, todayHoliday, shiftNames);

            List<PayslipDto> currentPayslips = currentYearPayslipsF.get().contentOrEmpty();
            List<PayslipDto> prevPayslips = prevYearPayslipsF.get().contentOrEmpty();

            return new EmployeeDashboard(
                    nextPayday(currentPayslips, prevPayslips),
                    today1,
                    needsYou(pendingLeavesF.get(), myOtF.get(), monthRecs, today, me.joiningDate()),
                    leaveBalance(balancesF.get(), typesF.get(), today),
                    heatmap(currentMonth, heatmapF.get()),
                    payslipsBlock(currentPayslips, prevPayslips),
                    upcomingHolidays(holidaysF.get(), today)
            );
        } catch (InterruptedException ie) {
            Thread.currentThread().interrupt();
            throw new RuntimeException("interrupted while assembling /bff/dashboard/employee", ie);
        } catch (ExecutionException ee) {
            throw DashboardSupport.unwrap(ee);
        }
    }

    private EmployeeDashboard.NextPayday nextPayday(List<PayslipDto> currentYear,
                                                     List<PayslipDto> prevYear) {
        PayslipDto latest = mostRecent(currentYear, prevYear);
        if (latest == null) return new EmployeeDashboard.NextPayday(null, null, null, null);
        // The latest payslip's run carries the canonical status. The "date" is
        // a best-effort: payroll-service doesn't surface a payday on the run
        // entity, so we use generatedAt as a placeholder for v1. The FE renders
        // status front-and-center anyway.
        try {
            PayrollRunDto run = payroll.run(latest.payrollRunId());
            LocalDate date = run.postedAt() != null
                    ? run.postedAt().toLocalDate()
                    : null;
            return new EmployeeDashboard.NextPayday(date,
                    Money.pkr(latest.net()), latest.payrollRunId(),
                    run.status() == null ? null : run.status().name());
        } catch (RuntimeException ex) {
            log.debug("payroll.run({}) failed for nextPayday slice: {}", latest.payrollRunId(), ex.getMessage());
            return new EmployeeDashboard.NextPayday(null, Money.pkr(latest.net()),
                    latest.payrollRunId(), null);
        }
    }

    private static PayslipDto mostRecent(List<PayslipDto> a,
                                                        List<PayslipDto> b) {
        PayslipDto best = null;
        for (List<PayslipDto> list : List.of(a, b)) {
            for (PayslipDto p : list) {
                if (best == null || isLater(p, best)) best = p;
            }
        }
        return best;
    }

    private static boolean isLater(PayslipDto candidate, PayslipDto current) {
        if (candidate.periodYear() != current.periodYear()) return candidate.periodYear() > current.periodYear();
        return candidate.periodMonth() > current.periodMonth();
    }

    private EmployeeDashboard.NeedsYou needsYou(List<LeaveRequestDto> pendingLeaves,
                                                 List<OvertimeRequestDto> myOt,
                                                 List<AttendanceRecordDto> monthRecs,
                                                 LocalDate today,
                                                 LocalDate joiningDate) {
        // Pending requests: caller's own pending leaves + caller's own pending OT.
        // myOt comes back unfiltered — narrow to PENDING here so the dashboard
        // matches the FE's "still waiting on you" expectation.
        List<EmployeeDashboard.RequestRow> pending = new ArrayList<>();
        for (LeaveRequestDto l : pendingLeaves) {
            pending.add(new EmployeeDashboard.RequestRow(
                    "leave", l.id(),
                    l.fromDate() + " → " + l.toDate() + " (" + l.leaveTypeCode() + ")",
                    l.status(), l.createdAt()));
        }
        for (OvertimeRequestDto o : myOt) {
            if (!"PENDING".equals(o.status())) continue;
            pending.add(new EmployeeDashboard.RequestRow(
                    "overtime", o.id(),
                    "OT on " + o.workDate(),
                    o.status(), o.createdAt()));
        }
        pending.sort(Comparator.comparing(EmployeeDashboard.RequestRow::createdAt,
                Comparator.nullsLast(Comparator.naturalOrder())).reversed());
        int pendingCount = pending.size();
        List<EmployeeDashboard.RequestRow> pendingTop = pending.subList(0, Math.min(NEEDS_YOU_TOP, pending.size()));

        // Missing punches: weekdays in the current month between the employee's
        // joining date and yesterday with no attendance record (so no punches
        // and the nightly close didn't leave a stub either). We deliberately
        // exclude today — a same-day "missing" wouldn't be actionable yet.
        Map<LocalDate, AttendanceRecordDto> byDate = new HashMap<>();
        for (AttendanceRecordDto r : monthRecs) byDate.put(r.workDate(), r);
        LocalDate from = today.withDayOfMonth(1);
        if (joiningDate != null && joiningDate.isAfter(from)) from = joiningDate;
        List<EmployeeDashboard.PunchRow> missing = new ArrayList<>();
        for (LocalDate d = from; d.isBefore(today); d = d.plusDays(1)) {
            if (d.getDayOfWeek() == DayOfWeek.SUNDAY) continue;
            if (byDate.containsKey(d)) continue;
            missing.add(new EmployeeDashboard.PunchRow(d, "no attendance record"));
        }
        // Newest first
        missing.sort(Comparator.comparing(EmployeeDashboard.PunchRow::date).reversed());
        int missingCount = missing.size();
        List<EmployeeDashboard.PunchRow> missingTop = missing.subList(0, Math.min(NEEDS_YOU_TOP, missing.size()));

        // Documents to acknowledge: no upstream contract yet. Empty for v1.
        return new EmployeeDashboard.NeedsYou(
                new EmployeeDashboard.CountedRequests(pendingCount, pendingTop),
                new EmployeeDashboard.CountedPunches(missingCount, missingTop),
                new EmployeeDashboard.CountedDocs(0, List.of()));
    }

    private EmployeeDashboard.LeaveBalance leaveBalance(List<LeaveBalanceDto> balances,
                                                         List<LeaveTypeDto> types,
                                                         LocalDate today) {
        Map<Long, LeaveTypeDto> typeById = new HashMap<>();
        for (LeaveTypeDto t : types) typeById.put(t.id(), t);

        List<EmployeeDashboard.LeaveTypeBalance> rows = new ArrayList<>();
        for (LeaveBalanceDto b : balances) {
            LeaveTypeDto type = typeById.get(b.leaveTypeId());
            if (type == null || !type.showOnDashboard()) continue;
            BigDecimal allocated = b.allocated() == null ? BigDecimal.ZERO : b.allocated();
            BigDecimal used = b.used() == null ? BigDecimal.ZERO : b.used();
            BigDecimal pending = b.pending() == null ? BigDecimal.ZERO : b.pending();
            BigDecimal available = b.available() == null ? BigDecimal.ZERO : b.available();
            rows.add(new EmployeeDashboard.LeaveTypeBalance(
                    b.leaveTypeCode(), type.name(),
                    used, pending, available, allocated));
        }
        return new EmployeeDashboard.LeaveBalance(today, rows);
    }

    private EmployeeDashboard.AttendanceHeatmap heatmap(YearMonth ym,
                                                        EmployeeHeatmapDto upstream) {
        // Server-side rollup (D-S5). Map upstream day rows 1:1 — the upstream
        // already accounts for HOLIDAY / WEEKEND / ABSENT fallbacks.
        if (upstream == null || upstream.days() == null) {
            return new EmployeeDashboard.AttendanceHeatmap(ym.format(MONTH_FMT), List.of());
        }
        List<EmployeeDashboard.HeatmapDay> days = upstream.days().stream()
                .filter(Objects::nonNull)
                .map(d -> new EmployeeDashboard.HeatmapDay(d.date(), d.status(), d.varianceMin()))
                .toList();
        String monthLabel = upstream.month() != null ? upstream.month() : ym.format(MONTH_FMT);
        return new EmployeeDashboard.AttendanceHeatmap(monthLabel, days);
    }

    private EmployeeDashboard.Payslips payslipsBlock(List<PayslipDto> currentYear,
                                                      List<PayslipDto> prevYear) {
        List<PayslipDto> all = new ArrayList<>(currentYear.size() + prevYear.size());
        all.addAll(currentYear);
        all.addAll(prevYear);
        // Newest first by (year, month).
        all.sort((a, b) -> {
            int y = Integer.compare(b.periodYear(), a.periodYear());
            return y != 0 ? y : Integer.compare(b.periodMonth(), a.periodMonth());
        });

        List<EmployeeDashboard.PayslipRow> recent = new ArrayList<>(RECENT_PAYSLIPS);
        for (PayslipDto p : all) {
            if (recent.size() >= RECENT_PAYSLIPS) break;
            recent.add(new EmployeeDashboard.PayslipRow(
                    p.id(), p.periodYear(), p.periodMonth(),
                    Money.pkr(p.gross()), Money.pkr(p.net()),
                    p.status() == null ? null : p.status().name(), p.generatedAt()));
        }

        // 12-month trend: oldest → newest so the FE chart renders left-to-right.
        // Bucket by period; pick the latest payslip per bucket if there are
        // duplicates (e.g. an off-cycle release in the same month).
        Map<String, java.math.BigDecimal> byPeriod = new HashMap<>();
        for (PayslipDto p : all) {
            String key = String.format("%04d-%02d", p.periodYear(), p.periodMonth());
            java.math.BigDecimal net = p.net() == null ? java.math.BigDecimal.ZERO : p.net();
            byPeriod.merge(key, net, java.math.BigDecimal::add);
        }
        List<EmployeeDashboard.NetTrendPoint> trend = new ArrayList<>(12);
        YearMonth cursor = YearMonth.now().minusMonths(11);
        for (int i = 0; i < 12; i++) {
            String key = cursor.format(MONTH_FMT);
            trend.add(new EmployeeDashboard.NetTrendPoint(key, byPeriod.getOrDefault(key, java.math.BigDecimal.ZERO)));
            cursor = cursor.plusMonths(1);
        }
        return new EmployeeDashboard.Payslips(recent, trend);
    }

    private List<EmployeeDashboard.UpcomingHoliday> upcomingHolidays(List<HolidayDto> holidays,
                                                                      LocalDate today) {
        return holidays.stream()
                .filter(h -> h.holidayDate().isAfter(today))
                .sorted(Comparator.comparing(HolidayDto::holidayDate))
                .limit(UPCOMING_HOLIDAYS)
                .map(h -> new EmployeeDashboard.UpcomingHoliday(
                        h.holidayDate(), h.name(),
                        h.holidayDate().getDayOfWeek().name().substring(0, 3)))
                .toList();
    }
}
