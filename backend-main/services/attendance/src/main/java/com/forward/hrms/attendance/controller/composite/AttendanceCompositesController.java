package com.forward.hrms.attendance.controller.composite;

import com.forward.hrms.attendance.dto.response.ApprovalsInbox;
import com.forward.hrms.attendance.dto.response.AttendanceMonth;
import com.forward.hrms.attendance.dto.response.AttendancePunchesPage;
import com.forward.hrms.attendance.dto.response.AttendanceRecordsPage;
import com.forward.hrms.attendance.dto.response.AttendanceToday;
import com.forward.hrms.attendance.dto.response.LeaveApplyContext;
import com.forward.hrms.attendance.dto.response.OperatorDashboard;
import com.forward.hrms.attendance.dto.response.OvertimeMineRow;
import com.forward.hrms.attendance.enums.AttendanceStatus;
import com.forward.hrms.attendance.enums.PunchSource;
import com.forward.hrms.attendance.service.AttendanceListingService;
import com.forward.hrms.attendance.service.composite.AttendanceCompositesService;
import com.forward.hrms.commons.web.security.RequirePermission;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;

import lombok.RequiredArgsConstructor;

import org.springframework.data.domain.Pageable;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.util.List;

/**
 * FE-facing composite endpoints for attendance surfaces. Each method
 * joins attendance data with employee summaries (via the shared cache)
 * in-process. Replaces the BFF's single-service composites that
 * fanned out via HTTP.
 *
 * <p>Cross-service composites (manager team-today / -month, which
 * resolve coverage via employee-service, and request timelines that
 * pair with audit-service) stay in BFF.
 */
@Tag(name = "Attendance — Composites",
        description = "Read-only composites that pair attendance rows with employee summaries " +
                "via the shared employee-display cache. Replaces the legacy BFF composites that " +
                "fanned out via HTTP for what is ultimately one service's data.")
@RestController
@RequiredArgsConstructor
public class AttendanceCompositesController {

    private final AttendanceCompositesService service;

    @Operation(summary = "Today screen composite (ESS)",
            description = "One round-trip Today view: today's record, today's punches, any " +
                    "approved leave covering today, today's holiday, the next upcoming holiday + " +
                    "approved leave, and a 3-bucket leave balance summary (annual / sick / casual).")
    @GetMapping("/attendance/me/today/composite")
    @PreAuthorize("isAuthenticated()")
    public AttendanceToday today() {
        return service.today();
    }

    @Operation(summary = "Calendar/month view (ESS)",
            description = "Per-day rows for the supplied month, pre-merged with holidays in the " +
                    "month and any approved leaves that overlap the month. Each day carries a " +
                    "`kind` render hint plus the underlying record / holiday / leave overlay.")
    @GetMapping("/attendance/me/month/composite")
    @PreAuthorize("isAuthenticated()")
    public AttendanceMonth month(
            @Parameter(description = "YYYY-MM, defaults to current month (Asia/Karachi).")
            @RequestParam(required = false) String month) {
        return service.month(month);
    }

    @Operation(summary = "My OT requests joined with attendance minutes (ESS)",
            description = "Returns the caller's OT requests with the attendance record's OT and " +
                    "worked-minutes for the same (employee, workDate) joined in. Minutes are zero " +
                    "when the OT is still PENDING / REJECTED, or when no attendance record exists " +
                    "yet for the day. Requires `attendance.read`.")
    @GetMapping("/overtime/me/with-records")
    @RequirePermission("attendance.read")
    public List<OvertimeMineRow> overtimeMine() {
        return service.overtimeMine();
    }

    @Operation(summary = "Org-wide attendance records with employee summary (HR / admin / operator)",
            description = "Sibling to `/attendance/records` — same filters and pagination, plus a " +
                    "compact `employee` summary on every row so the FE can render the table without " +
                    "an extra fan-out per row. Requires `attendance.admin` OR `attendance.operator`.")
    @GetMapping("/attendance/records/with-employee")
    @RequirePermission({"attendance.admin", "attendance.operator"})
    public AttendanceRecordsPage recordsWithEmployee(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @RequestParam(required = false) Long employeeId,
            @RequestParam(required = false) String employeeCode,
            @RequestParam(required = false) Long departmentId,
            @RequestParam(required = false) Long sectionId,
            @RequestParam(required = false) AttendanceStatus status,
            @RequestParam(required = false) Boolean lateOnly,
            @RequestParam(required = false) Boolean missingCheckout,
            @Parameter(hidden = true) Pageable pageable) {
        return service.recordsWithEmployee(
                new AttendanceListingService.RecordFilter(
                        date, from, to, employeeId, employeeCode, departmentId, sectionId,
                        status, lateOnly, missingCheckout, null),
                pageable);
    }

    @Operation(summary = "Org-wide punches with employee summary (HR / admin / operator)",
            description = "Sibling to `/attendance/punches` — same filters and pagination, plus a " +
                    "compact `employee` summary on every row. Primary list view for the " +
                    "attendance-operator's daily reconciliation. Requires `attendance.admin` OR " +
                    "`attendance.operator`.")
    @GetMapping("/attendance/punches/with-employee")
    @RequirePermission({"attendance.admin", "attendance.operator"})
    public AttendancePunchesPage punchesWithEmployee(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @RequestParam(required = false) Long employeeId,
            @RequestParam(required = false) String employeeCode,
            @RequestParam(required = false) Long departmentId,
            @RequestParam(required = false) Long sectionId,
            @RequestParam(required = false) PunchSource source,
            @RequestParam(required = false) Long deviceId,
            @Parameter(hidden = true) Pageable pageable) {
        return service.punchesWithEmployee(
                new AttendanceListingService.PunchFilter(
                        date, from, to, employeeId, employeeCode, departmentId, sectionId,
                        source, deviceId, null),
                pageable);
    }

    @Operation(summary = "Operator dashboard composite",
            description = "One-shot landing payload for the attendance-operator UI: org pulse, " +
                    "today's punch / override / pending-regularization counts, and the two action " +
                    "queues (missing checkouts, late arrivals) enriched with employee summaries. " +
                    "Default date is today (server-local Asia/Karachi). `period` limits every " +
                    "employee-scoped figure to employees whose record for the date is on a DAY / " +
                    "NIGHT shift; pending regularizations stay unfiltered. Requires " +
                    "`attendance.admin` OR `attendance.operator`.")
    @GetMapping("/attendance/operator/dashboard")
    @RequirePermission({"attendance.admin", "attendance.operator"})
    public OperatorDashboard operatorDashboard(
            @Parameter(description = "Date to roll up. Defaults to today (Asia/Karachi).")
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date,
            @Parameter(description = "Day or night shifts only. Omit for all.")
            @RequestParam(required = false) com.forward.hrms.attendance.enums.ShiftPeriod period) {
        return service.operatorDashboard(date, period);
    }

    @Operation(summary = "Apply-leave drawer context",
            description = "Single round-trip bundle for the apply-leave drawer: leave types catalog, " +
                    "caller's current-year balances, and holidays in the next 90 days.")
    @GetMapping("/leaves/apply-context")
    @PreAuthorize("isAuthenticated()")
    public LeaveApplyContext leaveApplyContext() {
        return service.leaveApplyContext();
    }

    @Operation(summary = "Combined approvals inbox",
            description = "One round-trip approvals inbox: leaves, overtime, and regularization " +
                    "pending-approval lists fetched in sequence, then enriched with an `employee` " +
                    "summary per row. Counts are the visible totals (not the size of the returned " +
                    "page). `leaveQ` / `leaveTypeId` filter the leaves bucket, `shortLeaveQ` the " +
                    "short-leave bucket, `overtimeQ` / `overtimeType` the overtime bucket, and " +
                    "`regularizationQ` the regularization bucket (see each type's " +
                    "`/pending-approvals`); a filtered bucket's count " +
                    "reflects the filter. Requires `attendance.read`.")
    @GetMapping("/approvals/inbox")
    @RequirePermission("attendance.read")
    public ApprovalsInbox approvalsInbox(
            @Parameter(description = "Leaves: request id, request code, employee code, or employee name.")
            @RequestParam(required = false) String leaveQ,
            @Parameter(description = "Leaves: exact leave type id.")
            @RequestParam(required = false) Long leaveTypeId,
            @Parameter(description = "Short leaves: request id, request code, employee code, or employee name.")
            @RequestParam(required = false) String shortLeaveQ,
            @Parameter(description = "Overtime: request id, request code, employee code, or employee name.")
            @RequestParam(required = false) String overtimeQ,
            @Parameter(description = "Overtime: exact overtime type.")
            @RequestParam(required = false) com.forward.hrms.attendance.enums.OvertimeType overtimeType,
            @Parameter(description = "Regularizations: request id, request code, employee code, or employee name.")
            @RequestParam(required = false) String regularizationQ) {
        return service.approvalsInbox(leaveQ, leaveTypeId, shortLeaveQ, overtimeQ, overtimeType,
                regularizationQ);
    }
}
