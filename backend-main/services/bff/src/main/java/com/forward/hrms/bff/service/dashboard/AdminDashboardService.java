package com.forward.hrms.bff.service.dashboard;

import com.forward.hrms.bff.client.AuditClient;
import com.forward.hrms.bff.client.AuthClient;
import com.forward.hrms.bff.client.SagaClient;
import com.forward.hrms.audit.dto.response.AuditEventDto;
import com.forward.hrms.bff.client.dto.PageView;
import com.forward.hrms.auth.dto.response.UserDto;
import com.forward.hrms.bff.dto.response.dashboard.AdminDashboard;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import org.springframework.stereotype.Service;

import tools.jackson.databind.JsonNode;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;

/**
 * Composite for {@code GET /bff/dashboard/admin}. Pulls audit health and
 * a small set of recent admin-relevant audit events. Several slices stay
 * empty until D-S4 (audit-side saga health rollup) lands.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AdminDashboardService {

    private static final int LOCKED_ACCOUNTS_FETCH = 25;
    private static final int RECENT_ROLE_CHANGES = 5;
    private static final int QUEUE_TOP = 5;
    private static final int SAGA_FETCH = 25;

    private final AuditClient audit;
    private final AuthClient auth;
    private final SagaClient sagas;
    private final DashboardSupport support;

    public AdminDashboard build() {
        // Soft slices — sections degrade to empty when the caller lacks
        // the relevant scope (audit.admin etc.) rather than failing the
        // whole composite.
        PageView<UserDto> emptyUsers = new PageView<>(List.of(), 0, LOCKED_ACCOUNTS_FETCH, 0L, 0, null);
        PageView<AuditEventDto> emptyAudit = new PageView<>(List.of(), 0, RECENT_ROLE_CHANGES, 0L, 0, null);
        PageView<SagaClient.SagaView> emptySagas = new PageView<>(List.of(), 0, SAGA_FETCH, 0L, 0, null);

        CompletableFuture<JsonNode> healthF = support.supplyOrDefault(audit::healthOrNull, null);
        CompletableFuture<PageView<UserDto>> lockedF =
                support.supplyOrDefault(() -> auth.adminUsers("LOCKED", LOCKED_ACCOUNTS_FETCH), emptyUsers);
        CompletableFuture<PageView<AuditEventDto>> roleChangesF = support.supplyOrDefault(() ->
                audit.search("role", null, null, null, RECENT_ROLE_CHANGES), emptyAudit);
        CompletableFuture<PageView<AuditEventDto>> permissionChangesF = support.supplyOrDefault(() ->
                audit.search("user", "role.attached", null, null, QUEUE_TOP), emptyAudit);
        CompletableFuture<PageView<SagaClient.SagaView>> employeeFailedF =
                support.supplyOrDefault(() -> sagas.employeeSagas("FAILED", SAGA_FETCH), emptySagas);
        CompletableFuture<PageView<SagaClient.SagaView>> payrollFailedF =
                support.supplyOrDefault(() -> sagas.payrollSagas("FAILED", SAGA_FETCH), emptySagas);
        CompletableFuture<SagaClient.SagaHealthView> employeeHealthF =
                support.supplyOrDefault(sagas::employeeSagasHealth, SagaClient.SagaHealthView.empty());
        CompletableFuture<SagaClient.SagaHealthView> payrollHealthF =
                support.supplyOrDefault(sagas::payrollSagasHealth, SagaClient.SagaHealthView.empty());

        try {
            CompletableFuture.allOf(healthF, lockedF, roleChangesF, permissionChangesF,
                    employeeFailedF, payrollFailedF, employeeHealthF, payrollHealthF).join();

            AdminDashboard.SystemHealth health = systemHealth(healthF.get(),
                    employeeHealthF.get(), payrollHealthF.get());
            AdminDashboard.NeedsYou needsYou = needsYou(lockedF.get(), permissionChangesF.get(),
                    employeeFailedF.get(), payrollFailedF.get());

            return new AdminDashboard(
                    health,
                    List.of(),
                    needsYou,
                    List.of(),
                    roleChangesF.get().contentOrEmpty());
        } catch (InterruptedException ie) {
            Thread.currentThread().interrupt();
            throw new RuntimeException("interrupted while assembling /bff/dashboard/admin", ie);
        } catch (ExecutionException ee) {
            throw DashboardSupport.unwrap(ee);
        }
    }

    private AdminDashboard.SystemHealth systemHealth(JsonNode health,
                                                      SagaClient.SagaHealthView employeeHealth,
                                                      SagaClient.SagaHealthView payrollHealth) {
        // /admin/audit/health returns per-topic, per-partition consumer lag. We
        // surface the simple "any backlog?" view: count topics with non-zero
        // lag and the worst lag we see. Anything richer needs the audit-side
        // rollup endpoint described in D-S4.
        int depth = 0;
        long oldestSeconds = 0;
        if (health != null) {
            JsonNode topics = health.path("topics");
            if (topics.isArray()) {
                for (JsonNode topic : topics) {
                    JsonNode partitions = topic.path("partitions");
                    if (!partitions.isArray()) continue;
                    for (JsonNode part : partitions) {
                        long lag = part.path("lag").asLong(0);
                        if (lag > 0) {
                            depth += (int) Math.min(Integer.MAX_VALUE, lag);
                            oldestSeconds = Math.max(oldestSeconds, lag);
                        }
                    }
                }
            }
        }

        // Saga rollup is the merge of the per-service /sagas/health endpoints.
        // Both services compute their own RUNNING count and 24h failure
        // buckets — concatenate the per-name lists, sum the totals.
        int running = employeeHealth.running() + payrollHealth.running();
        int failed24h = employeeHealth.failed24h() + payrollHealth.failed24h();
        List<AdminDashboard.SagaHealth> list = new ArrayList<>(
                employeeHealth.perNameOrEmpty().size() + payrollHealth.perNameOrEmpty().size());
        for (SagaClient.SagaHealthView.PerName p : employeeHealth.perNameOrEmpty()) {
            list.add(new AdminDashboard.SagaHealth(p.name(), p.name(), p.status(), p.failureRate24h()));
        }
        for (SagaClient.SagaHealthView.PerName p : payrollHealth.perNameOrEmpty()) {
            list.add(new AdminDashboard.SagaHealth(p.name(), p.name(), p.status(), p.failureRate24h()));
        }
        AdminDashboard.SagaSummary sagaSummary = new AdminDashboard.SagaSummary(running, failed24h, list);

        boolean sagasUnhealthy = failed24h > 0;
        String overall;
        if (depth == 0 && !sagasUnhealthy) overall = "OK";
        else if (depth >= 100 || failed24h > 5) overall = "INCIDENT";
        else overall = "DEGRADED";

        return new AdminDashboard.SystemHealth(
                overall,
                sagaSummary,
                new AdminDashboard.ReplayQueue(depth, oldestSeconds));
    }

    private AdminDashboard.NeedsYou needsYou(PageView<UserDto> locked,
                                              PageView<AuditEventDto> permissionChanges,
                                              PageView<SagaClient.SagaView> employeeFailed,
                                              PageView<SagaClient.SagaView> payrollFailed) {
        List<AdminDashboard.QueueItem> lockedRows = new ArrayList<>();
        for (UserDto u : locked.contentOrEmpty()) {
            if (lockedRows.size() >= QUEUE_TOP) break;
            lockedRows.add(new AdminDashboard.QueueItem(
                    u.id(),
                    u.fullName() == null ? u.username() : u.fullName(),
                    "Locked until " + (u.lockedUntil() == null ? "indefinite" : u.lockedUntil().toString()),
                    null));
        }
        AdminDashboard.QueueSection lockedSection = new AdminDashboard.QueueSection(
                (int) locked.totalElements(), lockedRows);

        List<AdminDashboard.QueueItem> permRows = new ArrayList<>();
        for (AuditEventDto e : permissionChanges.contentOrEmpty()) {
            if (permRows.size() >= QUEUE_TOP) break;
            permRows.add(new AdminDashboard.QueueItem(
                    e.id(),
                    e.actorLabel() == null ? e.actorUsername() : e.actorLabel(),
                    e.message() == null ? e.action() : e.message(),
                    null));
        }
        AdminDashboard.QueueSection permSection = new AdminDashboard.QueueSection(
                (int) permissionChanges.totalElements(), permRows);

        // Failed sagas: merge employee + payroll lists, top-N by recency.
        List<SagaClient.SagaView> merged = new ArrayList<>();
        merged.addAll(employeeFailed.contentOrEmpty());
        merged.addAll(payrollFailed.contentOrEmpty());
        merged.sort(Comparator.comparing(SagaClient.SagaView::updatedAt,
                Comparator.nullsLast(Comparator.naturalOrder())).reversed());
        List<AdminDashboard.QueueItem> sagaRows = new ArrayList<>();
        for (SagaClient.SagaView s : merged) {
            if (sagaRows.size() >= QUEUE_TOP) break;
            // Retry endpoints differ between services — derive from the
            // saga name's namespace which controller owns the row.
            String retryHref = isPayrollSaga(s)
                    ? "/payroll/sagas/" + s.id() + "/retry"
                    : "/sagas/" + s.id() + "/retry";
            sagaRows.add(new AdminDashboard.QueueItem(
                    s.id(),
                    s.sagaName(),
                    s.lastError() == null ? s.currentStep() : s.lastError(),
                    retryHref));
        }
        long failedTotal = employeeFailed.totalElements() + payrollFailed.totalElements();
        AdminDashboard.QueueSection failedSagas = new AdminDashboard.QueueSection(
                (int) Math.min(Integer.MAX_VALUE, failedTotal), sagaRows);

        return new AdminDashboard.NeedsYou(failedSagas, permSection, lockedSection);
    }

    /** Payroll-owned saga names — anything else is owned by employee-service. */
    private static boolean isPayrollSaga(SagaClient.SagaView s) {
        if (s.sagaName() == null) return false;
        return s.sagaName().startsWith("PAYROLL_") || s.sagaName().startsWith("BONUS_")
                || s.sagaName().startsWith("INCREMENT_");
    }
}
