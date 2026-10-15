package com.forward.hrms.bff.service;

import com.forward.hrms.attendance.dto.response.ApprovalDecisionRow;
import com.forward.hrms.bff.client.AttendanceClient;
import com.forward.hrms.bff.client.EmployeeClient;
import com.forward.hrms.bff.client.PayrollClient;
import com.forward.hrms.bff.dto.response.CombinedApprovalHistory;
import com.forward.hrms.bff.service.dashboard.DashboardSupport;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.function.Supplier;

/**
 * Composite for {@code GET /bff/approvals/history}: fans out to
 * attendance's, payroll's, and employee's own {@code /approvals/history}
 * in parallel — each already unified across its own request types (see
 * {@link ApprovalDecisionRow}'s class doc for the full list) — then merges
 * into one list sorted newest-decided-first. Mirrors
 * {@link ApprovalsInboxCompositeService}'s soft-fail-per-source doctrine.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ApprovalHistoryCompositeService {

    private final AttendanceClient attendanceClient;
    private final PayrollClient payrollClient;
    private final EmployeeClient employeesClient;
    private final DashboardSupport support;

    /** Request types each source decides. A {@code type} filter only fans out to its owner. */
    private static final Set<String> ATTENDANCE_TYPES = Set.of(
            "LEAVE", "SHORT_LEAVE", "OVERTIME", "REGULARIZATION");
    private static final Set<String> PAYROLL_TYPES = Set.of(
            "PERMANENT_ADVANCE", "SHORT_TERM_ADVANCE");
    private static final Set<String> EMPLOYEE_TYPES = Set.of(
            "REQUISITION_NEW_HIRE", "REQUISITION_REPLACEMENT",
            "TRAVEL_REQUEST", "TRAVEL_ADVANCE", "TRAVEL_SETTLEMENT");

    /**
     * {@code type} and {@code decision} are optional (null = all). With a
     * type, only the owning source is called — the others would either
     * 400 on an unknown enum (attendance) or return nothing. An unknown
     * type calls no source and yields an empty result.
     */
    public CombinedApprovalHistory build(LocalDate from, LocalDate to, String type, String decision) {
        CompletableFuture<SourceResult<List<ApprovalDecisionRow>>> attendanceF = owns(ATTENDANCE_TYPES, type)
                ? supplyTracked(() -> attendanceClient.approvalHistory(from, to, type, decision), "attendance")
                : skipped();
        CompletableFuture<SourceResult<List<ApprovalDecisionRow>>> payrollF = owns(PAYROLL_TYPES, type)
                ? supplyTracked(() -> payrollClient.approvalHistory(from, to, type, decision), "payroll")
                : skipped();
        CompletableFuture<SourceResult<List<ApprovalDecisionRow>>> employeeF = owns(EMPLOYEE_TYPES, type)
                ? supplyTracked(() -> employeesClient.approvalHistory(from, to, type, decision), "employee")
                : skipped();

        CompletableFuture.allOf(attendanceF, payrollF, employeeF).join();

        SourceResult<List<ApprovalDecisionRow>> att = attendanceF.join();
        SourceResult<List<ApprovalDecisionRow>> pay = payrollF.join();
        SourceResult<List<ApprovalDecisionRow>> emp = employeeF.join();

        List<ApprovalDecisionRow> rows = new ArrayList<>(
                att.value().size() + pay.value().size() + emp.value().size());
        rows.addAll(att.value());
        rows.addAll(pay.value());
        rows.addAll(emp.value());
        rows.sort(Comparator.comparing(ApprovalDecisionRow::decidedAt).reversed());

        List<String> erroredSources = new ArrayList<>(3);
        if (att.errored()) erroredSources.add("attendance");
        if (pay.errored()) erroredSources.add("payroll");
        if (emp.errored()) erroredSources.add("employee");

        return new CombinedApprovalHistory(rows, rows.size(), erroredSources);
    }

    /**
     * Runs {@code supplier} on the identity-propagating executor and never
     * lets a failure escape: on error the source is logged and the future
     * resolves to an empty list tagged {@code errored=true} instead of
     * failing the whole composite.
     */
    private CompletableFuture<SourceResult<List<ApprovalDecisionRow>>> supplyTracked(
            Supplier<List<ApprovalDecisionRow>> supplier, String source) {
        return support.supply(supplier).handle((value, ex) -> {
            if (ex == null) return new SourceResult<>(value, false);
            Throwable root = ex instanceof CompletionException ce && ce.getCause() != null ? ce.getCause() : ex;
            log.warn("approval-history source '{}' failed, returning empty bucket: {}", source, root.getMessage());
            return new SourceResult<>(List.<ApprovalDecisionRow>of(), true);
        });
    }

    private static boolean owns(Set<String> sourceTypes, String type) {
        return type == null || sourceTypes.contains(type);
    }

    private static CompletableFuture<SourceResult<List<ApprovalDecisionRow>>> skipped() {
        return CompletableFuture.completedFuture(new SourceResult<>(List.of(), false));
    }

    private record SourceResult<T>(T value, boolean errored) {}
}
