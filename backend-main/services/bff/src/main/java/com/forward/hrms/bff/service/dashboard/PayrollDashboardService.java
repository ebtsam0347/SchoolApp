package com.forward.hrms.bff.service.dashboard;

import com.forward.hrms.bff.client.PayrollClient;
import com.forward.hrms.bff.client.dto.PageView;
import com.forward.hrms.employee.dto.response.EmployeeSummary;
import com.forward.hrms.commons.cache.EmployeeSummaryMapper;
import com.forward.hrms.bff.dto.response.dashboard.Money;
import com.forward.hrms.bff.dto.response.dashboard.PayrollDashboard;
import com.forward.hrms.commons.cache.EmployeeDisplay;
import com.forward.hrms.commons.cache.EmployeeDisplayCache;
import com.forward.hrms.payroll.dto.response.PayslipDto;
import com.forward.hrms.payroll.dto.response.PayrollRunDto;
import com.forward.hrms.payroll.dto.response.PayrollRunTotalsDto;
import com.forward.hrms.payroll.enums.PayrollRunStatus;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;

/**
 * Composite for {@code GET /bff/dashboard/payroll}. Mirrors the existing
 * payroll BFF rollup pattern — fetch a run's payslips at a generous size
 * cap and summarize.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PayrollDashboardService {

    private static final int RUNS_TREND = 6;
    private static final int QUEUE_TOP = 5;
    private static final int QUEUE_FETCH_SIZE = 50;

    private final PayrollClient payroll;
    private final EmployeeDisplayCache displayCache;
    private final DashboardSupport support;

    public PayrollDashboard build() {
        // Soft-fail slices — a missing payroll.read scope surfaces empty
        // sections rather than killing the whole composite.
        PageView<PayslipDto> emptyPayslips =
                new PageView<>(List.of(), 0, QUEUE_FETCH_SIZE, 0L, 0, null);

        CompletableFuture<PayrollClient.FiscalYearView> fyF =
                support.supplyOrDefault(payroll::currentFiscalYearOrNull, null);
        CompletableFuture<PageView<PayslipDto>> heldF =
                support.supplyOrDefault(() -> payroll.payslipsByStatus("HELD", 0, QUEUE_FETCH_SIZE),
                        emptyPayslips);
        CompletableFuture<PageView<PayslipDto>> releasePendingF =
                support.supplyOrDefault(() -> payroll.payslipsByStatus("RELEASE_PENDING", 0, QUEUE_FETCH_SIZE),
                        emptyPayslips);

        try {
            CompletableFuture.allOf(fyF, heldF, releasePendingF).join();

            PayrollClient.FiscalYearView fy = fyF.get();
            List<PayrollRunDto> runs = runsOrEmpty(fy);
            // Newest first.
            List<PayrollRunDto> sortedRuns = new ArrayList<>(runs);
            sortedRuns.sort(Comparator
                    .comparingInt(PayrollRunDto::periodYear)
                    .thenComparingInt(PayrollRunDto::periodMonth)
                    .reversed());

            PayrollRunDto currentRun = pickCurrentRun(sortedRuns);
            PayrollDashboard.CurrentRun currentRunPayload = currentRun == null ? null
                    : buildCurrentRun(currentRun, fy);

            // 6m trend: take up to the last 6 runs and summarize each in
            // parallel. The same rollup logic now lives in payroll-service's
            // PayrollCompositesService — duplicated here on purpose to keep
            // the dashboard service self-contained over its own slim
            // PayrollClient calls (no extra HTTP round-trip per run).
            List<PayrollRunDto> trendRuns = sortedRuns.size() > RUNS_TREND
                    ? sortedRuns.subList(0, RUNS_TREND)
                    : sortedRuns;
            // Reverse so the chart renders oldest-on-the-left.
            List<PayrollRunDto> trendOldestFirst = new ArrayList<>(trendRuns);
            java.util.Collections.reverse(trendOldestFirst);
            List<CompletableFuture<PayrollDashboard.RunRollup>> rollupFutures =
                    new ArrayList<>(trendOldestFirst.size());
            for (PayrollRunDto r : trendOldestFirst) {
                rollupFutures.add(support.supplyOrDefault(() -> rollup(r), emptyRollup(r)));
            }
            CompletableFuture.allOf(rollupFutures.toArray(new CompletableFuture[0])).join();
            List<PayrollDashboard.RunRollup> rollups = new ArrayList<>(rollupFutures.size());
            for (CompletableFuture<PayrollDashboard.RunRollup> f : rollupFutures) rollups.add(f.get());

            PayrollDashboard.NeedsYou needsYou = needsYou(heldF.get(), releasePendingF.get());

            // Anomaly scatter, bonus-by-dept, net-YoY trend rely on aggregations
            // payroll-service doesn't expose yet (D-S3 plus extras). Surface
            // empty so the FE can render placeholder cards.
            return new PayrollDashboard(
                    currentRunPayload,
                    new PayrollDashboard.Runs6m(rollups),
                    needsYou,
                    null,
                    List.of(),
                    List.of());
        } catch (InterruptedException ie) {
            Thread.currentThread().interrupt();
            throw new RuntimeException("interrupted while assembling /bff/dashboard/payroll", ie);
        } catch (ExecutionException ee) {
            throw DashboardSupport.unwrap(ee);
        }
    }

    /** "Current" = most recent run that isn't POSTED, or the most recent overall. */
    private static PayrollRunDto pickCurrentRun(List<PayrollRunDto> sortedRuns) {
        if (sortedRuns.isEmpty()) return null;
        for (PayrollRunDto r : sortedRuns) {
            if (r.status() != PayrollRunStatus.POSTED) return r;
        }
        return sortedRuns.get(0);
    }

    private PayrollDashboard.CurrentRun buildCurrentRun(PayrollRunDto run,
                                                         PayrollClient.FiscalYearView fy) {
        PayrollRunTotalsDto t = totalsOrEmpty(run.id());
        String period = String.format("%04d-%02d", run.periodYear(), run.periodMonth());
        // No cutoff on the run row; surface null so the FE doesn't lie.
        return new PayrollDashboard.CurrentRun(
                run.id(),
                fy == null ? null : fy.code(),
                period,
                run.status(),
                null,
                null,
                new PayrollDashboard.Rollup(
                        Money.pkr(t.gross()),
                        Money.pkr(t.deductions()),
                        Money.pkr(t.net()),
                        (int) t.employeeCount()));
    }

    private List<PayrollRunDto> runsOrEmpty(PayrollClient.FiscalYearView fy) {
        if (fy == null) return List.of();
        try {
            return payroll.runsForFiscalYear(fy.code());
        } catch (RuntimeException ex) {
            log.debug("payroll.runsForFiscalYear({}) failed: {}", fy.code(), ex.getMessage());
            return List.of();
        }
    }

    private static PayrollDashboard.RunRollup emptyRollup(PayrollRunDto run) {
        return new PayrollDashboard.RunRollup(
                run.id(),
                String.format("%04d-%02d", run.periodYear(), run.periodMonth()),
                Money.pkr(java.math.BigDecimal.ZERO),
                Money.pkr(java.math.BigDecimal.ZERO),
                Money.pkr(java.math.BigDecimal.ZERO));
    }

    private PayrollDashboard.RunRollup rollup(PayrollRunDto run) {
        // §7.bff #13 — single GET against payroll's pre-aggregated totals
        // endpoint. Was 5000 payslip rows pulled per run × 6 runs per
        // dashboard render; now one tiny row per run.
        PayrollRunTotalsDto t = totalsOrEmpty(run.id());
        return new PayrollDashboard.RunRollup(
                run.id(),
                String.format("%04d-%02d", run.periodYear(), run.periodMonth()),
                Money.pkr(t.gross()), Money.pkr(t.deductions()), Money.pkr(t.net()));
    }

    private PayrollRunTotalsDto totalsOrEmpty(long runId) {
        try {
            return payroll.runTotals(runId, null, null);
        } catch (RuntimeException ex) {
            log.debug("payroll.runTotals({}) failed: {}", runId, ex.getMessage());
            java.math.BigDecimal z = java.math.BigDecimal.ZERO;
            return new PayrollRunTotalsDto(runId, 0, z, z, z, z, z, z, z, z, 0L, 0L);
        }
    }

    private PayrollDashboard.NeedsYou needsYou(PageView<PayslipDto> held,
                                                PageView<PayslipDto> releasePending) {
        Set<Long> employeeIds = new HashSet<>();
        for (PayslipDto p : held.contentOrEmpty()) employeeIds.add(p.employeeId());
        for (PayslipDto p : releasePending.contentOrEmpty()) employeeIds.add(p.employeeId());
        Map<Long, EmployeeDisplay> hits = displayCache.getMany(employeeIds);
        Map<Long, EmployeeSummary> summaries = new java.util.HashMap<>(hits.size());
        for (Map.Entry<Long, EmployeeDisplay> en : hits.entrySet()) {
            summaries.put(en.getKey(), EmployeeSummaryMapper.toSummary(en.getValue()));
        }

        List<PayrollDashboard.QueueItem> heldRows = new ArrayList<>();
        for (PayslipDto p : held.contentOrEmpty()) {
            if (heldRows.size() >= QUEUE_TOP) break;
            heldRows.add(new PayrollDashboard.QueueItem(
                    p.id(),
                    summaries.get(p.employeeId()),
                    String.format("%04d-%02d payslip", p.periodYear(), p.periodMonth()),
                    p.heldReason(),
                    p.generatedAt()));
        }
        // RELEASE_PENDING isn't in the wishlist's needsYou list; the hint in
        // the wishlist is "payslipsHeld" only. Combine HELD + RELEASE_PENDING
        // counts to mirror the existing PayslipQueue convention but only the
        // HELD rows make the actionable preview.
        int payslipsHeldCount = (int) (held.totalElements() + releasePending.totalElements());

        return new PayrollDashboard.NeedsYou(
                new PayrollDashboard.QueueSection(0, List.of()),
                new PayrollDashboard.QueueSection(0, List.of()),
                new PayrollDashboard.QueueSection(0, List.of()),
                new PayrollDashboard.QueueSection(payslipsHeldCount, heldRows));
    }
}
