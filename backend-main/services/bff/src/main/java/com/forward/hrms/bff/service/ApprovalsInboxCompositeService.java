package com.forward.hrms.bff.service;

import com.forward.hrms.attendance.dto.response.ApprovalsInbox;
import com.forward.hrms.bff.client.AttendanceClient;
import com.forward.hrms.bff.client.EmployeeClient;
import com.forward.hrms.bff.client.PayrollClient;
import com.forward.hrms.bff.client.dto.PageView;
import com.forward.hrms.bff.dto.response.CombinedApprovalsInbox;
import com.forward.hrms.bff.service.dashboard.DashboardSupport;
import com.forward.hrms.commons.cache.EmployeeDisplayCache;
import com.forward.hrms.commons.cache.EmployeeSummaryMapper;
import com.forward.hrms.employee.dto.response.EmployeeSummary;
import com.forward.hrms.employee.dto.response.FactoryToursInbox;
import com.forward.hrms.employee.dto.response.RequisitionDto;
import com.forward.hrms.employee.dto.response.TravelRequestResponse;
import com.forward.hrms.payroll.dto.response.PermanentAdvanceDto;
import com.forward.hrms.payroll.dto.response.PermanentAdvanceInboxRow;
import com.forward.hrms.payroll.dto.response.ShortTermAdvanceInboxRow;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.function.Supplier;

/**
 * Composite for {@code GET /bff/approvals/inbox}: fans out to attendance's
 * own combined inbox (leave + overtime + regularization + short leave,
 * already employee-enriched), employee-service's requisitions, factory-tours
 * and travel-requests inboxes, and payroll's two advance inboxes — all in
 * parallel, all caller-scoped by the downstream service the same way the
 * individual endpoints already are.
 *
 * <p>No source failure fails the whole composite: each fan-out is wrapped in
 * {@link #supplyTracked}, which soft-fails to an empty bucket and records
 * the source name in {@code erroredSources} rather than propagating.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ApprovalsInboxCompositeService {

    /** Single large page per source — the composite merges, no further pagination round-trip. */
    private static final int PAGED_FETCH_SIZE = 200;

    private final AttendanceClient attendance;
    private final EmployeeClient employees;
    private final PayrollClient payroll;
    private final EmployeeDisplayCache displayCache;
    private final DashboardSupport support;

    public CombinedApprovalsInbox build(String leaveQ, Long leaveTypeId, String shortLeaveQ,
                                        String overtimeQ, String overtimeType,
                                        String regularizationQ,
                                        String requisitionQ, String requisitionType,
                                        Long requisitionDepartmentId,
                                        String shortTermAdvanceQ,
                                        String permanentAdvanceQ,
                                        String travelQ, String travelTypeCode,
                                        java.time.LocalDate travelFrom, java.time.LocalDate travelTo) {
        CompletableFuture<SourceResult<ApprovalsInbox>> attendanceF = supplyTracked(
                () -> attendance.approvalsInbox(leaveQ, leaveTypeId, shortLeaveQ, overtimeQ, overtimeType,
                        regularizationQ), "attendance",
                new ApprovalsInbox(List.of(), List.of(), List.of(), List.of(),
                        new ApprovalsInbox.Counts(0, 0, 0, 0, 0)));

        CompletableFuture<SourceResult<PageView<RequisitionDto>>> requisitionsF = supplyTracked(
                () -> employees.requisitionsPending(PAGED_FETCH_SIZE, requisitionQ, requisitionType,
                        requisitionDepartmentId), "requisitions", emptyPage());

//        CompletableFuture<SourceResult<FactoryToursInbox>> factoryToursF = supplyTracked(
//                employees::factoryToursPending, "factory-tours",
//                new FactoryToursInbox(List.of(), new FactoryToursInbox.Counts(0, 0)));

        CompletableFuture<SourceResult<PageView<TravelRequestResponse>>> travelRequestsF = supplyTracked(
                () -> employees.travelRequestsPending(PAGED_FETCH_SIZE, travelQ, travelTypeCode, travelFrom, travelTo),
                "travel-requests", emptyPage());

        CompletableFuture<SourceResult<PageView<PermanentAdvanceInboxRow>>> permanentAdvancesF = supplyTracked(
                () -> payroll.permanentAdvancesPending(PAGED_FETCH_SIZE, permanentAdvanceQ), "permanent-advances", emptyPage());

        CompletableFuture<SourceResult<PageView<ShortTermAdvanceInboxRow>>> shortTermAdvancesF = supplyTracked(
                () -> payroll.shortTermAdvancesPending(PAGED_FETCH_SIZE, shortTermAdvanceQ), "short-term-advances", emptyPage());

//        CompletableFuture.allOf(attendanceF, requisitionsF, factoryToursF, travelRequestsF,
//                permanentAdvancesF, shortTermAdvancesF).join();
        CompletableFuture.allOf(attendanceF, requisitionsF, travelRequestsF,
                permanentAdvancesF, shortTermAdvancesF).join();

        SourceResult<ApprovalsInbox> att = attendanceF.join();
        SourceResult<PageView<RequisitionDto>> req = requisitionsF.join();
//        SourceResult<FactoryToursInbox> tours = factoryToursF.join();
        SourceResult<PageView<TravelRequestResponse>> travel = travelRequestsF.join();
        SourceResult<PageView<PermanentAdvanceInboxRow>> pa = permanentAdvancesF.join();
        SourceResult<PageView<ShortTermAdvanceInboxRow>> sta = shortTermAdvancesF.join();

        // Only permanent advances carry a single employeeId to resolve
        // (requisitions are department-level; short-term batches span N
        // employees whose detail lines aren't included on the list read).
        Set<Long> employeeIds = new LinkedHashSet<>();
        for (PermanentAdvanceInboxRow row : pa.value().contentOrEmpty()) {
            employeeIds.add(row.request().employeeId());
        }
        Map<Long, EmployeeSummary> summaries = employeeIds.isEmpty() ? Map.of() : resolveSummaries(employeeIds);

        List<CombinedApprovalsInbox.PermanentAdvanceRow> permanentAdvanceRows =
                new ArrayList<>(pa.value().contentOrEmpty().size());
        for (PermanentAdvanceInboxRow row : pa.value().contentOrEmpty()) {
            permanentAdvanceRows.add(new CombinedApprovalsInbox.PermanentAdvanceRow(
                    row.request(), row.approvalStage(), summaries.get(row.request().employeeId())));
        }

        List<CombinedApprovalsInbox.ShortTermAdvanceRow> shortTermAdvanceRows =
                new ArrayList<>(sta.value().contentOrEmpty().size());
        for (ShortTermAdvanceInboxRow row : sta.value().contentOrEmpty()) {
            shortTermAdvanceRows.add(new CombinedApprovalsInbox.ShortTermAdvanceRow(
                    row.request(), row.approvalStage()));
        }

        List<RequisitionDto> requisitionRows = req.value().contentOrEmpty();
//        List<FactoryToursInbox.Row> factoryTourRows = tours.value().factoryTours();
        List<TravelRequestResponse> travelRequestRows = travel.value().contentOrEmpty();

        int requisitionsTotal = (int) req.value().totalElements();
        int travelRequestsTotal = (int) travel.value().totalElements();
        int permanentAdvancesTotal = (int) pa.value().totalElements();
        int shortTermAdvancesTotal = (int) sta.value().totalElements();
//        int total = att.value().counts().total() + requisitionsTotal + tours.value().counts().total()
//                + travelRequestsTotal + permanentAdvancesTotal + shortTermAdvancesTotal;
        int total = att.value().counts().total() + requisitionsTotal + travelRequestsTotal + permanentAdvancesTotal
                + shortTermAdvancesTotal;

        List<String> erroredSources = new ArrayList<>(6);
        if (att.errored()) erroredSources.add("attendance");
        if (req.errored()) erroredSources.add("requisitions");
//        if (tours.errored()) erroredSources.add("factory-tours");
        if (travel.errored()) erroredSources.add("travel-requests");
        if (pa.errored()) erroredSources.add("permanent-advances");
        if (sta.errored()) erroredSources.add("short-term-advances");

        return new CombinedApprovalsInbox(
                att.value().leaves(), att.value().overtime(), att.value().regularization(),
                att.value().shortLeave(),
                requisitionRows, travelRequestRows, permanentAdvanceRows, shortTermAdvanceRows,
                new CombinedApprovalsInbox.Counts(
                        att.value().counts().leaves(), att.value().counts().overtime(),
                        att.value().counts().regularization(), att.value().counts().shortLeave(),
                        requisitionsTotal, travelRequestsTotal,
                        permanentAdvancesTotal, shortTermAdvancesTotal,
                        total),
                erroredSources);
    }

    private Map<Long, EmployeeSummary> resolveSummaries(Set<Long> employeeIds) {
        Map<Long, com.forward.hrms.commons.cache.EmployeeDisplay> displays = displayCache.getMany(employeeIds);
        Map<Long, EmployeeSummary> out = new java.util.HashMap<>(displays.size() * 2);
        for (Map.Entry<Long, com.forward.hrms.commons.cache.EmployeeDisplay> e : displays.entrySet()) {
            out.put(e.getKey(), EmployeeSummaryMapper.toSummary(e.getValue()));
        }
        return out;
    }

    private static <T> PageView<T> emptyPage() {
        return new PageView<>(List.of(), 0, PAGED_FETCH_SIZE, 0L, 0, null);
    }

    /**
     * Runs {@code supplier} on the identity-propagating executor and never
     * lets a failure escape: on error the source is logged and the future
     * resolves to {@code fallback} tagged {@code errored=true} instead of
     * failing the whole composite.
     */
    private <T> CompletableFuture<SourceResult<T>> supplyTracked(Supplier<T> supplier, String source, T fallback) {
        return support.supply(supplier).handle((value, ex) -> {
            if (ex == null) return new SourceResult<>(value, false);
            Throwable root = ex instanceof CompletionException ce && ce.getCause() != null ? ce.getCause() : ex;
            log.warn("approvals-inbox source '{}' failed, returning empty bucket: {}", source, root.getMessage());
            return new SourceResult<>(fallback, true);
        });
    }

    private record SourceResult<T>(T value, boolean errored) {}
}
