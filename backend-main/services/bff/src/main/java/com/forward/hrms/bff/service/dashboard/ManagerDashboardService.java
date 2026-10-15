package com.forward.hrms.bff.service.dashboard;

import com.forward.hrms.bff.client.AttendanceClient;
import com.forward.hrms.bff.client.EmployeeClient;
import com.forward.hrms.employee.dto.response.EmployeeDto;
import com.forward.hrms.employee.dto.response.EmployeeSummary;
import com.forward.hrms.commons.cache.EmployeeSummaryMapper;
import com.forward.hrms.bff.dto.response.dashboard.ManagerDashboard;
import com.forward.hrms.commons.cache.CatalogCache;
import com.forward.hrms.attendance.dto.response.ApprovalDecisionRow;
import com.forward.hrms.attendance.dto.response.AttendanceRecordDto;
import com.forward.hrms.attendance.dto.response.LeaveRequestDto;
import com.forward.hrms.attendance.dto.response.OvertimeRequestDto;
import com.forward.hrms.attendance.dto.response.RegularizationRequestDto;
import com.forward.hrms.commons.core.error.CommonErrorCode;
import com.forward.hrms.commons.core.error.ForbiddenError;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;

/**
 * Composite for {@code GET /bff/dashboard/manager}. Scope is the caller's
 * direct reports — 403 to anyone without reports, the FE then drops back to
 * the Employee dashboard.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ManagerDashboardService {

    private static final int APPROVAL_QUEUE_TOP = 5;
    private static final int ATTENDANCE_RANGE_DAYS = 14;
    private static final int CONTEXT_WINDOW_DAYS = 7;
    private static final int LEAVE_CALENDAR_DAYS = 30;

    private final EmployeeClient employees;
    private final AttendanceClient attendance;
    private final CatalogCache catalogCache;
    private final DashboardSupport support;

    public ManagerDashboard build() {
        // /employees/me/team is the caller-scoped read — open to any
        // authenticated employee, returns the caller's direct reports.
        List<EmployeeDto> team = employees.myTeam();
        if (team == null || team.isEmpty()) {
            throw new ForbiddenError(CommonErrorCode.FORBIDDEN,
                    "manager dashboard requires direct reports");
        }
        EmployeeDto me = employees.me();
        Set<Long> reportIds = new LinkedHashSet<>();
        for (EmployeeDto r : team) reportIds.add(r.id());

        LocalDate today = LocalDate.now();
        LocalDate from = today.minusDays(ATTENDANCE_RANGE_DAYS - 1L);
        LocalDate calendarTo = today.plusDays(LEAVE_CALENDAR_DAYS);

        // Per-report fan-out for the 14d range. /attendance/employee/{id}/range
        // requires attendance.read; if the manager hasn't been granted that
        // scope the slice surfaces empty rather than failing the whole
        // composite — matches the doc'd intent "missing scope surfaces zeros".
        List<CompletableFuture<List<AttendanceRecordDto>>> rangeFutures =
                new ArrayList<>(team.size());
        for (EmployeeDto r : team) {
            rangeFutures.add(support.supplyOrDefault(
                    () -> attendance.employeeRange(r.id(), from, today),
                    List.of()));
        }

        // Approval queues plus the team summaries we'll need to enrich rows.
        CompletableFuture<List<LeaveRequestDto>> leavePendingF =
                support.supplyOrDefault(attendance::leavePending, List.of());
        CompletableFuture<List<OvertimeRequestDto>> otPendingF =
                support.supplyOrDefault(attendance::overtimePending, List.of());
        CompletableFuture<List<RegularizationRequestDto>> regPendingF =
                support.supplyOrDefault(attendance::regularizationPending, List.of());

        // Caller-scoped approvals closed in the last 7 days — feeds the
        // approval-velocity sparkline. Soft-fails to empty list so the
        // composite still renders when /approvals/history is misbehaving.
        LocalDate contextFrom = today.minusDays(CONTEXT_WINDOW_DAYS - 1L);
        CompletableFuture<List<ApprovalDecisionRow>> approvalHistoryF =
                support.supplyOrDefault(
                        () -> attendance.approvalHistory(contextFrom, today),
                        List.of());

        try {
            CompletableFuture.allOf(joinAll(rangeFutures,
                    List.of(leavePendingF, otPendingF, regPendingF, approvalHistoryF))).join();

            Map<LocalDate, ManagerDashboard.DayBucket.Builder> bucketBuilders = new LinkedHashMap<>();
            for (LocalDate d = from; !d.isAfter(today); d = d.plusDays(1)) {
                bucketBuilders.put(d, new ManagerDashboard.DayBucket.Builder(d));
            }
            // Last-7-day aggregates for the Context tiles. Punctuality buckets
            // sum lateMinutes across PRESENT/LATE records (people who actually
            // showed up); exceptions count LATE + ABSENT across reports.
            Map<LocalDate, long[]> latePerDay = new LinkedHashMap<>(); // [sumMinutes, count]
            for (LocalDate d = contextFrom; !d.isAfter(today); d = d.plusDays(1)) {
                latePerDay.put(d, new long[]{0L, 0L});
            }
            int exceptionsCount = 0;
            for (CompletableFuture<List<AttendanceRecordDto>> f : rangeFutures) {
                for (AttendanceRecordDto r : f.get()) {
                    ManagerDashboard.DayBucket.Builder b = bucketBuilders.get(r.workDate());
                    if (b != null) b.add(r.status());
                    if (!r.workDate().isBefore(contextFrom) && !r.workDate().isAfter(today)) {
                        String st = r.status();
                        if ("PRESENT".equals(st) || "LATE".equals(st)) {
                            long[] agg = latePerDay.get(r.workDate());
                            agg[0] += Math.max(0, r.lateMinutes());
                            agg[1] += 1;
                        }
                        if ("LATE".equals(st) || "ABSENT".equals(st)) exceptionsCount++;
                    }
                }
            }
            List<ManagerDashboard.DayBucket> days = new ArrayList<>(bucketBuilders.size());
            for (ManagerDashboard.DayBucket.Builder b : bucketBuilders.values()) days.add(b.build());

            ManagerDashboard.Punctuality punctuality = punctuality(latePerDay);

            // Team rows are already loaded EmployeeDtos — only catalog labels need resolving.
            CatalogCache.CatalogIndex idx = catalogCache.snapshot();
            Map<Long, EmployeeSummary> teamSummaries = new HashMap<>(team.size());
            for (EmployeeDto r : team) teamSummaries.put(r.id(), EmployeeSummaryMapper.toSummary(r, idx));

            // Approval queue: filter the global pending lists to the manager's
            // reports. The upstream pending-approvals endpoints are caller-
            // scoped (a manager only sees what they can decide on), so this
            // is a defence-in-depth filter.
            List<LeaveRequestDto> leaves = leavePendingF.get();
            List<OvertimeRequestDto> ots = otPendingF.get();
            List<RegularizationRequestDto> regs = regPendingF.get();

            ManagerDashboard.Velocity velocity = velocity(approvalHistoryF.get(), contextFrom, today);
            ManagerDashboard.ApprovalQueue queue = approvalQueue(reportIds, leaves, ots, regs, teamSummaries, velocity);

            ManagerDashboard.LeaveCalendar30d calendar = leaveCalendar(leaves, calendarTo, today, teamSummaries);

            // Preserve team order for the avatar stack; teamSummaries is a HashMap.
            List<EmployeeSummary> members = new ArrayList<>(team.size());
            for (EmployeeDto r : team) members.add(teamSummaries.get(r.id()));

            return new ManagerDashboard(
                    new ManagerDashboard.Team(team.size(), team.size(), members),
                    new ManagerDashboard.Attendance14d(from, today, days),
                    queue,
                    calendar,
                    new ManagerDashboard.Context(
                            punctuality,
                            new ManagerDashboard.Exceptions(exceptionsCount)));
        } catch (InterruptedException ie) {
            Thread.currentThread().interrupt();
            throw new RuntimeException("interrupted while assembling /bff/dashboard/manager for " + me.id(), ie);
        } catch (ExecutionException ee) {
            throw DashboardSupport.unwrap(ee);
        }
    }

    private ManagerDashboard.ApprovalQueue approvalQueue(Set<Long> reportIds,
                                                          List<LeaveRequestDto> leaves,
                                                          List<OvertimeRequestDto> ots,
                                                          List<RegularizationRequestDto> regs,
                                                          Map<Long, EmployeeSummary> teamSummaries,
                                                          ManagerDashboard.Velocity velocity) {
        List<ManagerDashboard.ApprovalRow> rows = new ArrayList<>();
        int total = 0;
        for (LeaveRequestDto l : leaves) {
            if (!reportIds.contains(l.employeeId())) continue;
            total++;
            rows.add(new ManagerDashboard.ApprovalRow(
                    "leave", l.id(), teamSummaries.get(l.employeeId()),
                    l.fromDate() + " → " + l.toDate() + " (" + l.leaveTypeCode() + ")",
                    l.createdAt()));
        }
        for (OvertimeRequestDto o : ots) {
            if (!reportIds.contains(o.employeeId())) continue;
            total++;
            rows.add(new ManagerDashboard.ApprovalRow(
                    "overtime", o.id(), teamSummaries.get(o.employeeId()),
                    "OT on " + o.workDate(),
                    o.createdAt()));
        }
        for (RegularizationRequestDto r : regs) {
            if (!reportIds.contains(r.employeeId())) continue;
            total++;
            rows.add(new ManagerDashboard.ApprovalRow(
                    "regularization", r.id(), teamSummaries.get(r.employeeId()),
                    "Regularization on " + r.workDate(),
                    r.createdAt()));
        }
        rows.sort(Comparator.comparing(ManagerDashboard.ApprovalRow::createdAt,
                Comparator.nullsLast(Comparator.naturalOrder())));
        List<ManagerDashboard.ApprovalRow> top = rows.subList(0, Math.min(APPROVAL_QUEUE_TOP, rows.size()));
        return new ManagerDashboard.ApprovalQueue(total, top, velocity);
    }

    private static ManagerDashboard.Punctuality punctuality(Map<LocalDate, long[]> latePerDay) {
        List<Integer> trend = new ArrayList<>(latePerDay.size());
        long sum = 0, count = 0;
        for (long[] agg : latePerDay.values()) {
            int dayAvg = agg[1] == 0 ? 0 : (int) Math.round((double) agg[0] / agg[1]);
            trend.add(dayAvg);
            sum += agg[0];
            count += agg[1];
        }
        int overall = count == 0 ? 0 : (int) Math.round((double) sum / count);
        return new ManagerDashboard.Punctuality(overall, trend);
    }

    private static ManagerDashboard.Velocity velocity(List<ApprovalDecisionRow> rows,
                                                       LocalDate from,
                                                       LocalDate to) {
        Map<LocalDate, long[]> perDay = new LinkedHashMap<>(); // [sumMinutes, count]
        for (LocalDate d = from; !d.isAfter(to); d = d.plusDays(1)) {
            perDay.put(d, new long[]{0L, 0L});
        }
        long sum = 0, count = 0;
        for (ApprovalDecisionRow r : rows) {
            if (r.decidedAt() == null || r.requestCreatedAt() == null) continue;
            LocalDate day = r.decidedAt().toLocalDate();
            long[] agg = perDay.get(day);
            if (agg == null) continue; // outside the 7-day window
            long minutes = Math.max(0, Duration.between(r.requestCreatedAt(), r.decidedAt()).toMinutes());
            agg[0] += minutes;
            agg[1] += 1;
            sum += minutes;
            count += 1;
        }
        List<Integer> trend = new ArrayList<>(perDay.size());
        for (long[] agg : perDay.values()) {
            trend.add(agg[1] == 0 ? 0 : (int) Math.round((double) agg[0] / agg[1]));
        }
        int overall = count == 0 ? 0 : (int) Math.round((double) sum / count);
        return new ManagerDashboard.Velocity(overall, trend);
    }

    private ManagerDashboard.LeaveCalendar30d leaveCalendar(List<LeaveRequestDto> leaves,
                                                              LocalDate calendarTo,
                                                              LocalDate today,
                                                              Map<Long, EmployeeSummary> teamSummaries) {
        // We only have the manager's pending-approval feed without a team-
        // approved-leave list endpoint. Surface upcoming pendings so the
        // calendar is populated; once D-S* lands we can flip this to APPROVED.
        List<ManagerDashboard.LeaveItem> items = new ArrayList<>();
        for (LeaveRequestDto l : leaves) {
            if (l.toDate().isBefore(today) || l.fromDate().isAfter(calendarTo)) continue;
            EmployeeSummary s = teamSummaries.get(l.employeeId());
            if (s == null) continue; // only show direct-report rows
            items.add(new ManagerDashboard.LeaveItem(
                    l.employeeId(),
                    s.fullName(),
                    l.leaveTypeCode(),
                    l.fromDate(),
                    l.toDate()));
        }
        items.sort(Comparator.comparing(ManagerDashboard.LeaveItem::start));
        return new ManagerDashboard.LeaveCalendar30d(today, calendarTo, items);
    }

    @SafeVarargs
    private static CompletableFuture<?>[] joinAll(List<? extends CompletableFuture<?>>... lists) {
        int n = 0;
        for (List<? extends CompletableFuture<?>> l : lists) n += l.size();
        CompletableFuture<?>[] out = new CompletableFuture[n];
        int i = 0;
        for (List<? extends CompletableFuture<?>> l : lists) for (CompletableFuture<?> f : l) out[i++] = f;
        return out;
    }
}
