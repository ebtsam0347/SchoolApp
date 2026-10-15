package com.forward.hrms.bff.service;

import com.forward.hrms.bff.client.EmployeeClient;
import com.forward.hrms.employee.dto.response.DisciplinaryActionListResponse;
import com.forward.hrms.employee.dto.response.EmployeeHistoryDto;
import com.forward.hrms.employee.dto.response.EmployeeHistoryRow;
import com.forward.hrms.employee.dto.response.EmployeeDto;
import com.forward.hrms.employee.dto.response.EmployeeResolvedLabels;
import com.forward.hrms.employee.dto.response.EmploymentContractDto;
import com.forward.hrms.employee.dto.response.ExitDto;
import com.forward.hrms.bff.client.dto.PageView;
import com.forward.hrms.employee.dto.response.SuspensionDto;
import com.forward.hrms.employee.enums.ContractStatus;
import com.forward.hrms.employee.enums.ContractType;
import com.forward.hrms.employee.enums.DisciplinaryStatus;
import com.forward.hrms.employee.enums.SuspensionStatus;
import com.forward.hrms.audit.dto.response.TimelineEntry;
import com.forward.hrms.audit.dto.response.TimelinePage;
import com.forward.hrms.bff.dto.response.AccountProfile;
import com.forward.hrms.bff.dto.response.EmployeeDetail;
import com.forward.hrms.employee.dto.response.EmployeeSummary;
import com.forward.hrms.commons.cache.EmployeeSummaryMapper;
import com.forward.hrms.bff.dto.response.EmployeesByIdsResponse;
import com.forward.hrms.commons.web.async.RequestPropagatingExecutor;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.function.Supplier;

/**
 * Cross-service employee composites that survive the
 * single-service-composite move (Step 4 of docs/caching.md).
 *
 * <p>{@code employeeDetail} and {@code timeline} both fan out across
 * employee-service and audit-service. {@code accountProfile} is
 * single-service today but lives here because it pairs an ESS-side
 * /me view with the same resolver helpers used by /people/{id}.
 * {@code byIds} is the generic identity resolver for chat / approvals
 * / OT batch surfaces.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class EmployeeBffService {

    private static final int DETAIL_TIMELINE_LIMIT = 20;
    private static final int DETAIL_HISTORY_LIMIT = 10;
    private static final int DIRECT_REPORTS_PREVIEW = 8;
    public static final int BY_IDS_MAX = 200;

    private final EmployeeClient employeeClient;
    private final com.forward.hrms.bff.client.AuditClient audit;
    private final com.forward.hrms.commons.cache.CatalogCache catalogCache;
    private final com.forward.hrms.commons.cache.EmployeeDisplayCache displayCache;

    private final RequestPropagatingExecutor propagating;

    // ===== A1: /bff/employee/{id} =====

    public EmployeeDetail employeeDetail(long id) {
        EmployeeDto e = employeeClient.getEmployee(id);

        CompletableFuture<List<EmploymentContractDto>> contractsF = supplyWithRequest(() -> employeeClient.contracts(id));
        CompletableFuture<List<SuspensionDto>> suspensionsF = supplyWithRequest(() -> employeeClient.suspensions(id));
        CompletableFuture<ExitDto> exitF = supplyWithRequest(() -> employeeClient.exitOrNull(id));
        CompletableFuture<DisciplinaryActionListResponse> disciplinaryF =
                supplyWithRequest(() -> employeeClient.disciplinaryFor(id));
        CompletableFuture<List<EmployeeDto>> reportsF = supplyWithRequest(() -> employeeClient.directReports(id));
        CompletableFuture<TimelinePage> timelineF =
                supplyWithRequest(() -> audit.entityTimeline("employee", String.valueOf(id),
                        DETAIL_TIMELINE_LIMIT, null));
        CompletableFuture<PageView<EmployeeHistoryDto>> historyF =
                supplyWithRequest(() -> employeeClient.history(id, DETAIL_HISTORY_LIMIT));
        CompletableFuture<EmployeeDto> managerF = e.managerId() == null
                ? CompletableFuture.completedFuture(null)
                : supplyWithRequest(() -> employeeClient.getEmployeeOrNull(e.managerId()));

        // §7.bff #46 — fold the catalog fetch into the same parallel stage
        // as the other downstream calls. The CatalogCache snapshot fans out
        // internally to Redis, but running it on the request thread would
        // serialize after the supplyWithRequest joins; the future overlaps
        // it with the rest of the slice work.
        CompletableFuture<com.forward.hrms.commons.cache.CatalogCache.CatalogIndex> bagF =
                supplyWithRequest(catalogCache::snapshot);

        try {
            CompletableFuture.allOf(contractsF, suspensionsF, exitF, disciplinaryF,
                    reportsF, timelineF, historyF, managerF, bagF).join();
            com.forward.hrms.commons.cache.CatalogCache.CatalogIndex bag = bagF.get();

            List<EmploymentContractDto> contracts = contractsF.get();
            EmploymentContractDto active = contracts.stream()
                    .filter(c -> c.status() == ContractStatus.ACTIVE).findFirst().orElse(null);
            SuspensionDto activeSuspension = suspensionsF.get().stream()
                    .filter(s -> s.status() == SuspensionStatus.ACTIVE).findFirst().orElse(null);

            DisciplinaryActionListResponse disc = disciplinaryF.get();
            int openIssued = disc == null || disc.actions() == null ? 0
                    : (int) disc.actions().stream().filter(a -> a.status() == DisciplinaryStatus.ISSUED).count();

            List<EmployeeDto> reports = reportsF.get();
            List<EmployeeDetail.ReportSummary> previewItems = reports.stream()
                    .limit(DIRECT_REPORTS_PREVIEW)
                    .map(r -> new EmployeeDetail.ReportSummary(
                            r.id(), r.fullName(), r.employeeCode(),
                            r.profilePictureUrl(),
                            r.directReportsCount() == null ? 0 : r.directReportsCount()))
                    .toList();

            List<EmployeeHistoryDto> historyRows = historyF.get() == null || historyF.get().content() == null
                    ? List.of() : historyF.get().content();

            Map<Long, EmployeeDto> managerLookup = buildManagerLookup(
                    managerF.get(),
                    collectHistoryManagerIds(historyRows, e.managerId()));

            EmployeeResolvedLabels resolved = EmployeeDetail.resolve(e, bag, managerLookup);

            List<EmployeeHistoryRow> resolvedHistory = historyRows.stream()
                    .map(h -> EmployeeDetail.historyRow(h, bag, managerLookup))
                    .toList();

            List<TimelineEntry> timeline = timelineF.get() == null || timelineF.get().content() == null
                    ? List.of()
                    : timelineF.get().content();

            long tenureDays = e.joiningDate() == null
                    ? 0
                    : Math.max(0, ChronoUnit.DAYS.between(e.joiningDate(), LocalDate.now()));

            Map<String, Long> warningCountsByName = (disc == null || disc.warningCounts() == null)
                    ? Map.of()
                    : disc.warningCounts().entrySet().stream()
                            .collect(java.util.stream.Collectors.toMap(
                                    en -> en.getKey().name(), Map.Entry::getValue));
            int totalReports = e.directReportsCount() == null ? reports.size() : e.directReportsCount();
            return new EmployeeDetail(
                    e, resolved, active, activeSuspension, exitF.get(),
                    warningCountsByName,
                    openIssued,
                    new EmployeeDetail.DirectReportsPreview(previewItems, totalReports),
                    timeline,
                    resolvedHistory,
                    tenureDays);
        } catch (InterruptedException ie) {
            Thread.currentThread().interrupt();
            throw new RuntimeException("interrupted while assembling employee detail " + id, ie);
        } catch (ExecutionException ee) {
            throw unwrap(ee);
        }
    }

    // ===== A4: /bff/employees/{id}/timeline =====

    public TimelinePage timeline(long employeeId, Integer limit, String cursor) {
        int n = limit == null || limit <= 0 ? 50 : Math.min(limit, 200);
        EmployeeDto e = employeeClient.getEmployee(employeeId);

        CompletableFuture<TimelinePage> entityF = supplyWithRequest(
                () -> audit.entityTimeline("employee", String.valueOf(employeeId), n, cursor));
        CompletableFuture<TimelinePage> userF = e.userId() == null
                ? CompletableFuture.completedFuture(null)
                : supplyWithRequest(() -> audit.entityTimeline("user", String.valueOf(e.userId()), n, cursor));

        try {
            CompletableFuture.allOf(entityF, userF).join();
            List<TimelineEntry> merged = new ArrayList<>();
            if (entityF.get() != null && entityF.get().content() != null) merged.addAll(entityF.get().content());
            if (userF.get() != null && userF.get().content() != null) merged.addAll(userF.get().content());
            // Newest first by occurredAt.
            merged.sort((a, b) -> b.occurredAt().compareTo(a.occurredAt()));
            List<TimelineEntry> bounded = merged.size() > n ? merged.subList(0, n) : merged;
            // Producers now set actorLabel on the envelope (X-User-FullName →
            // EventEnvelope.actorLabel → audit.actor_label), so the BFF no
            // longer resolves it at read time.
            String next = entityF.get() == null ? null : entityF.get().nextCursor();
            return new TimelinePage(bounded, next);
        } catch (InterruptedException ie) {
            Thread.currentThread().interrupt();
            throw new RuntimeException("interrupted while assembling employee timeline " + employeeId, ie);
        } catch (ExecutionException ee) {
            throw unwrap(ee);
        }
    }

    // ===== E3: /bff/account/profile =====

    public AccountProfile accountProfile() {
        EmployeeDto me = employeeClient.me();

        CompletableFuture<List<EmploymentContractDto>> contractsF = supplyWithRequest(employeeClient::myContracts);
        CompletableFuture<List<EmployeeDto>> teamF = supplyWithRequest(employeeClient::myTeam);
        // /employees/me/manager is open to any authenticated employee — using
        // /employees/{id} here would 403 ESS users that lack employee.read.
        CompletableFuture<EmployeeDto> managerF = me.managerId() == null
                ? CompletableFuture.completedFuture(null)
                : supplyWithRequest(employeeClient::myManagerOrNull);

        com.forward.hrms.commons.cache.CatalogCache.CatalogIndex bag = catalogCache.snapshot();

        try {
            CompletableFuture.allOf(contractsF, teamF, managerF).join();
            EmploymentContractDto active = contractsF.get().stream()
                    .filter(c -> c.status() == ContractStatus.ACTIVE).findFirst().orElse(null);

            Map<Long, EmployeeDto> managerLookup = managerF.get() == null
                    ? Map.of()
                    : Map.of(managerF.get().id(), managerF.get());
            EmployeeResolvedLabels resolved = EmployeeDetail.resolve(me, bag, managerLookup);

            List<EmployeeDetail.ReportSummary> team = teamF.get().stream()
                    .map(r -> new EmployeeDetail.ReportSummary(
                            r.id(), r.fullName(), r.employeeCode(),
                            r.profilePictureUrl(),
                            r.directReportsCount() == null ? 0 : r.directReportsCount()))
                    .toList();

            LocalDate probationDeadline = me.activeProbation() == null ? null : me.activeProbation().endDate();
            // The /me read on employee-service doesn't surface activeProbation; cover both shapes:
            if (probationDeadline == null && active != null && active.contractType() == ContractType.PROBATION) {
                probationDeadline = active.endDate();
            }

            return new AccountProfile(me, resolved, active, team, probationDeadline);
        } catch (InterruptedException ie) {
            Thread.currentThread().interrupt();
            throw new RuntimeException("interrupted while assembling /bff/account/profile", ie);
        } catch (ExecutionException ee) {
            throw unwrap(ee);
        }
    }

    // ===== T4: /bff/employees/by-ids =====

    /**
     * Resolve a bare list of employee ids into compact summaries. Order
     * preserved from the request; ids that don't resolve (404, lack of
     * permission upstream) are silently dropped. Use the response length
     * vs. the request length if exact-match matters.
     */
    public EmployeesByIdsResponse byIds(List<Long> ids) {
        if (ids == null || ids.isEmpty()) return new EmployeesByIdsResponse(List.of());
        Map<Long, com.forward.hrms.commons.cache.EmployeeDisplay> hits = displayCache.getMany(ids);

        // De-dupe input while preserving first-seen order so the response
        // mirrors the request shape (callers can compare lengths to detect
        // dropped rows).
        List<EmployeeSummary> rows = new ArrayList<>();
        Set<Long> seen = new HashSet<>();
        for (Long id : ids) {
            if (!seen.add(id)) continue;
            com.forward.hrms.commons.cache.EmployeeDisplay d = hits.get(id);
            if (d != null) rows.add(EmployeeSummaryMapper.toSummary(d));
        }
        return new EmployeesByIdsResponse(rows);
    }

    // ===== shared helpers =====

    /** Combine a known manager (already fetched) with extra ids resolved via parallel fan-out. */
    private Map<Long, EmployeeDto> buildManagerLookup(EmployeeDto known, Set<Long> extraIds) {
        Map<Long, EmployeeDto> out = new HashMap<>();
        if (known != null) out.put(known.id(), known);
        Set<Long> toFetch = new HashSet<>(extraIds);
        if (known != null) toFetch.remove(known.id());
        if (toFetch.isEmpty()) return out;

        List<CompletableFuture<EmployeeDto>> futures = new ArrayList<>(toFetch.size());
        for (Long id : toFetch) {
            futures.add(supplyWithRequest(() -> employeeClient.getEmployeeOrNull(id)));
        }
        try {
            CompletableFuture.allOf(futures.toArray(new CompletableFuture[0])).join();
            for (CompletableFuture<EmployeeDto> f : futures) {
                EmployeeDto v = f.get();
                if (v != null) out.putIfAbsent(v.id(), v);
            }
            return out;
        } catch (InterruptedException ie) {
            Thread.currentThread().interrupt();
            return out;
        } catch (ExecutionException ee) {
            throw unwrap(ee);
        }
    }

    private static Set<Long> collectHistoryManagerIds(List<EmployeeHistoryDto> rows, Long includeId) {
        Set<Long> ids = new HashSet<>();
        if (includeId != null) ids.add(includeId);
        for (EmployeeHistoryDto h : rows) {
            if (h.managerId() != null) ids.add(h.managerId());
        }
        return ids;
    }

    private <T> CompletableFuture<T> supplyWithRequest(Supplier<T> supplier) {
        return propagating.async(supplier);
    }

    /** §7.bff #25 — delegates to {@link com.forward.hrms.bff.service.dashboard.DashboardSupport#unwrap}. */
    private static RuntimeException unwrap(ExecutionException ee) {
        return com.forward.hrms.bff.service.dashboard.DashboardSupport.unwrap(ee);
    }
}
