package com.forward.hrms.bff.service.dashboard;

import com.forward.hrms.bff.client.AttendanceClient;
import com.forward.hrms.bff.client.AuditClient;
import com.forward.hrms.bff.client.EmployeeClient;
import com.forward.hrms.employee.dto.response.AggregateDto;
import com.forward.hrms.audit.dto.response.AuditEventDto;
import com.forward.hrms.employee.dto.response.DisciplinaryActionDto;
import com.forward.hrms.employee.dto.response.EmployeeDto;
import com.forward.hrms.bff.client.dto.PageView;
import com.forward.hrms.audit.dto.response.TimelineEntry;
import com.forward.hrms.employee.dto.response.EmployeeSummary;
import com.forward.hrms.commons.cache.EmployeeSummaryMapper;
import com.forward.hrms.bff.dto.response.dashboard.HrDashboard;
import com.forward.hrms.commons.cache.EmployeeDisplay;
import com.forward.hrms.commons.cache.EmployeeDisplayCache;
import com.forward.hrms.employee.dto.response.DepartmentDto;
import com.forward.hrms.attendance.dto.response.OrgPulseDto;
import com.forward.hrms.attendance.dto.response.OrgAttendanceDayDto;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;

/**
 * Composite for {@code GET /bff/dashboard/hr}. Org-wide totals are the
 * headline; queues are filtered by caller scope where the upstream
 * endpoint enforces it.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class HrDashboardService {

    private static final int QUEUE_TOP = 5;
    private static final int CONTRACTS_DUE_WINDOW_DAYS = 30;
    private static final int LIST_FETCH_SIZE = 50;
    private static final int RECENT_ANOMALIES = 3;
    private static final int ATTENDANCE_TREND_DAYS = 30;
    private static final int HEADCOUNT_MONTHS = 12;

    private final EmployeeClient employees;
    private final AttendanceClient attendance;
    private final AuditClient audit;
    private final EmployeeDisplayCache displayCache;
    private final DashboardSupport support;

    public HrDashboard build() {
        LocalDate today = LocalDate.now();
        LocalDate from30 = today.minusDays(ATTENDANCE_TREND_DAYS - 1L);

        // All slices are soft — per the doc'd intent ("missing scope
        // surfaces zeros rather than failing the whole call"), a 4xx
        // from any single slice falls back to the empty defaults below.
        AggregateDto emptyAgg = new AggregateDto("status", 0L, List.of());
        PageView<EmployeeDto> emptyEmployees = new PageView<>(List.of(), 0, LIST_FETCH_SIZE, 0L, 0, null);
        PageView<DisciplinaryActionDto> emptyDisciplinary =
                new PageView<>(List.of(), 0, LIST_FETCH_SIZE, 0L, 0, null);
        PageView<AuditEventDto> emptyAudit =
                new PageView<>(List.of(), 0, RECENT_ANOMALIES, 0L, 0, null);
        EmployeeClient.HeadcountTrendView emptyHeadcount =
                new EmployeeClient.HeadcountTrendView(HEADCOUNT_MONTHS, List.of());

        CompletableFuture<AggregateDto> statusAggF =
                support.supplyOrDefault(() -> employees.aggregate("status"), emptyAgg);
        CompletableFuture<PageView<EmployeeDto>> stuckEssF = support.supplyOrDefault(
                () -> employees.listEmployees(EmployeeClient.SearchParams.empty()
                        .withStatus("ACTIVE").withHasEss(false).withSize(LIST_FETCH_SIZE)),
                emptyEmployees);
        CompletableFuture<PageView<EmployeeDto>> probationDueF = support.supplyOrDefault(
                () -> employees.listEmployees(EmployeeClient.SearchParams.empty()
                        .withProbationDueWithin(CONTRACTS_DUE_WINDOW_DAYS)
                        .withSize(LIST_FETCH_SIZE)),
                emptyEmployees);
        CompletableFuture<PageView<DisciplinaryActionDto>> disciplinaryF =
                support.supplyOrDefault(() -> employees.disciplinaryByStatus("ISSUED", LIST_FETCH_SIZE),
                        emptyDisciplinary);
        CompletableFuture<List<com.forward.hrms.employee.dto.response.DepartmentDto>> deptsF =
                support.supplyOrDefault(employees::departments, List.of());
        CompletableFuture<com.forward.hrms.bff.client.dto.PageView<AuditEventDto>> auditAnomalyF =
                support.supplyOrDefault(() -> audit.search(null, "ANOMALY",
                        from30.atStartOfDay(),
                        null, RECENT_ANOMALIES),
                        emptyAudit);
        // New (D-S2 + headcount-trend) — org-pulse, daily attendance trend,
        // and the 12m headcount series come back from dedicated server-
        // side aggregates rather than reducing per-employee rows.
        CompletableFuture<OrgPulseDto> pulseF =
                support.supplyOrDefault(() -> attendance.orgPulse(today), null);
        CompletableFuture<List<OrgAttendanceDayDto>> attendance30dF =
                support.supplyOrDefault(() -> attendance.orgAttendance(from30, today), List.of());
        CompletableFuture<EmployeeClient.HeadcountTrendView> headcountF =
                support.supplyOrDefault(() -> employees.headcountTrend(HEADCOUNT_MONTHS), emptyHeadcount);

        try {
            CompletableFuture.allOf(statusAggF, stuckEssF, probationDueF,
                    disciplinaryF, deptsF, auditAnomalyF,
                    pulseF, attendance30dF, headcountF).join();

            // Headcount = sum of non-TERMINATED status buckets, mirrors the
            // same convention employee-stats uses.
            long headcount = 0;
            for (AggregateDto.Bucket b : statusAggF.get().buckets()) {
                if (!"TERMINATED".equals(b.key())) headcount += b.count();
            }

            HrDashboard.Queues queues = queues(stuckEssF.get(), probationDueF.get(),
                    disciplinaryF.get(), deptsF.get());
            List<TimelineEntry> anomalies = auditAnomalyF.get().contentOrEmpty().stream()
                    .map(e -> new TimelineEntry(
                            e.id(), e.action(), e.actorUserId(), e.actorUsername(), e.actorLabel(),
                            e.message(), e.occurredAt()))
                    .toList();

            return new HrDashboard(
                    orgPulse(today, headcount, pulseF.get()),
                    attendance30d(from30, today, attendance30dF.get()),
                    headcount12m(headcountF.get()),
                    queues,
                    List.of(),
                    anomalies);
        } catch (InterruptedException ie) {
            Thread.currentThread().interrupt();
            throw new RuntimeException("interrupted while assembling /bff/dashboard/hr", ie);
        } catch (ExecutionException ee) {
            throw DashboardSupport.unwrap(ee);
        }
    }

    private static HrDashboard.OrgPulse orgPulse(LocalDate today, long headcount,
                                                  OrgPulseDto pulse) {
        if (pulse == null) {
            return new HrDashboard.OrgPulse(today, headcount, 0, 0, 0, 0);
        }
        return new HrDashboard.OrgPulse(today, headcount,
                pulse.present(), pulse.late(), pulse.onLeave(), pulse.absent());
    }

    private static HrDashboard.Attendance30d attendance30d(LocalDate from, LocalDate to,
                                                            List<OrgAttendanceDayDto> days) {
        if (days == null || days.isEmpty()) {
            return new HrDashboard.Attendance30d(from, to, List.of());
        }
        // rate = (present + halfDay/2) / total — matches the convention HR
        // already uses on the operator pages.
        double[] rates = new double[days.size()];
        int i = 0;
        for (OrgAttendanceDayDto d : days) {
            rates[i++] = d.total() == 0 ? 0
                    : (d.present() + d.halfDay() / 2.0) / d.total();
        }
        // Trailing-14d 1σ band — matches the wishlist's "anomaly if >1σ
        // outside trailing 14d" phrasing without bringing in a stats lib.
        List<HrDashboard.RateDay> out = new ArrayList<>(days.size());
        for (int k = 0; k < days.size(); k++) {
            double rate = rates[k];
            int wfrom = Math.max(0, k - 14);
            double mean = 0, var = 0;
            int n = 0;
            for (int j = wfrom; j < k; j++) { mean += rates[j]; n++; }
            mean = n == 0 ? rate : mean / n;
            for (int j = wfrom; j < k; j++) {
                double dx = rates[j] - mean;
                var += dx * dx;
            }
            double std = n > 1 ? Math.sqrt(var / (n - 1)) : 0;
            boolean anomaly = n >= 7 && std > 0 && Math.abs(rate - mean) > std;
            out.add(new HrDashboard.RateDay(days.get(k).date(), round4(rate), anomaly));
        }
        return new HrDashboard.Attendance30d(from, to, out);
    }

    private static double round4(double v) {
        return Math.round(v * 10000.0) / 10000.0;
    }

    private static HrDashboard.Headcount12m headcount12m(EmployeeClient.HeadcountTrendView trend) {
        if (trend == null || trend.series() == null) return new HrDashboard.Headcount12m(List.of());
        List<HrDashboard.HeadcountMonth> months = trend.series().stream()
                .map(m -> new HrDashboard.HeadcountMonth(m.month(), m.joiners(), m.leavers(), m.endHeadcount()))
                .toList();
        return new HrDashboard.Headcount12m(months);
    }

    private HrDashboard.Queues queues(PageView<EmployeeDto> stuckEss,
                                       PageView<EmployeeDto> probationDue,
                                       PageView<DisciplinaryActionDto> disciplinary,
                                       List<com.forward.hrms.employee.dto.response.DepartmentDto> depts) {
        // Resolve employee summaries for every row we'll surface in one batch.
        Set<Long> ids = new HashSet<>();
        for (EmployeeDto e : stuckEss.contentOrEmpty()) ids.add(e.id());
        for (EmployeeDto e : probationDue.contentOrEmpty()) ids.add(e.id());
        for (DisciplinaryActionDto d : disciplinary.contentOrEmpty()) ids.add(d.employeeId());

        Map<Long, EmployeeDisplay> hits = displayCache.getMany(ids);
        Map<Long, EmployeeSummary> summaries = new HashMap<>(hits.size());
        for (Map.Entry<Long, EmployeeDisplay> en : hits.entrySet()) {
            summaries.put(en.getKey(), EmployeeSummaryMapper.toSummary(en.getValue()));
        }

        // Onboarding stuck — caller-side filter to ESS_PENDING / ESS_FAILED.
        // The upstream listEmployees only filters by hasEss, so we narrow
        // here to "needs ESS but it hasn't landed".
        List<HrDashboard.QueueItem> stuckItems = new ArrayList<>();
        int stuckCount = 0;
        for (EmployeeDto e : stuckEss.contentOrEmpty()) {
            com.forward.hrms.employee.enums.EssProvisioningStatus statusEnum = e.essProvisioningStatus();
            String status = statusEnum == null ? null : statusEnum.name();
            if (status == null || "GRANTED".equals(status) || "REVOKED".equals(status)) continue;
            stuckCount++;
            if (stuckItems.size() < QUEUE_TOP) {
                stuckItems.add(new HrDashboard.QueueItem("onboarding", e.id(),
                        summaries.get(e.id()),
                        "ESS provisioning " + status,
                        null));
            }
        }
        HrDashboard.QueueSection onboardingStuck = new HrDashboard.QueueSection(stuckCount, stuckItems);

        // Contracts expiring — for v1 we surface probation-due as the
        // closest signal until employee-service exposes a contract-expiry
        // list. activeProbation.endDate gives us the deadline.
        List<HrDashboard.QueueItem> contractItems = new ArrayList<>();
        for (EmployeeDto e : probationDue.contentOrEmpty()) {
            if (contractItems.size() >= QUEUE_TOP) break;
            com.forward.hrms.employee.dto.response.ActiveProbationDto p = e.activeProbation();
            contractItems.add(new HrDashboard.QueueItem(
                    "probation", e.id(), summaries.get(e.id()),
                    "Probation ends " + (p == null ? "soon" : p.endDate()),
                    p == null ? null : p.endDate()));
        }
        contractItems.sort(Comparator.comparing(HrDashboard.QueueItem::due,
                Comparator.nullsLast(Comparator.naturalOrder())));
        HrDashboard.QueueSection contractsExpiring = new HrDashboard.QueueSection(
                (int) probationDue.totalElements(),
                contractItems.subList(0, Math.min(QUEUE_TOP, contractItems.size())));

        // Disciplinary — ISSUED rows need HR follow-up.
        List<HrDashboard.QueueItem> discItems = new ArrayList<>();
        for (DisciplinaryActionDto d : disciplinary.contentOrEmpty()) {
            if (discItems.size() >= QUEUE_TOP) break;
            discItems.add(new HrDashboard.QueueItem(
                    "disciplinary", d.id(), summaries.get(d.employeeId()),
                    d.level() + " — " + d.category(),
                    d.issuedOn()));
        }
        HrDashboard.QueueSection disciplinaryOpen = new HrDashboard.QueueSection(
                (int) disciplinary.totalElements(), discItems);

        // Leave hotspots — needs an aggregate over leave_balances we don't
        // have. Empty for v1.
        return new HrDashboard.Queues(
                onboardingStuck,
                contractsExpiring,
                disciplinaryOpen,
                new HrDashboard.LeaveHotspots(0, List.of()));
    }
}
