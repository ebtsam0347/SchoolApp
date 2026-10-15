package com.forward.hrms.attendance.controller;

import com.forward.hrms.attendance.client.EmployeeClient;
import com.forward.hrms.attendance.dto.request.*;
import com.forward.hrms.attendance.dto.response.*;
import com.forward.hrms.attendance.enums.AttendanceStatus;
import com.forward.hrms.attendance.enums.PunchSource;
import com.forward.hrms.attendance.service.AttendanceListingService;
import com.forward.hrms.attendance.service.AttendanceQueryService;
import com.forward.hrms.attendance.service.CallerContext;
import com.forward.hrms.attendance.service.PunchService;
import com.forward.hrms.commons.web.page.PageResponse;
import com.forward.hrms.commons.web.page.PageableDefaults;
import com.forward.hrms.commons.web.security.RequirePermission;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.time.YearMonth;
import java.util.List;

@Tag(name = "Attendance",
        description = "Punches + daily attendance records. Self-portal punching, operator-added " +
                "punches (single + bulk), operator amend / delete, biometric batch ingest, and " +
                "per-employee read views. " +
                "Requires `attendance.read` / `attendance.write` / `attendance.operator` / " +
                "`attendance.admin`.")
@RestController
@RequestMapping("/attendance")
@RequiredArgsConstructor
public class AttendanceController {

    private final PunchService punches;
    private final AttendanceQueryService query;
    private final AttendanceListingService listing;
    private final CallerContext caller;
    private final com.forward.hrms.attendance.service.AutoAttendanceService autoAttendance;

    @Operation(summary = "Self-punch (ESS)",
            description = "Records a portal punch for the calling employee. Cap of 2 WEB punches per day. Requires `attendance.write`.")
    @ApiResponse(responseCode = "201", description = "Punch recorded.")
    @ApiResponse(responseCode = "409", description = "PUNCH_LIMIT — third WEB punch on the same day.")
    @PostMapping("/punch")
    @RequirePermission("attendance.write")
    @ResponseStatus(HttpStatus.CREATED)
    public PunchDto punch(@RequestBody(required = false) PunchRequest body) {
        EmployeeClient.EmployeeView me = caller.requireEmployee();
        return PunchDtoMapper.of(punches.recordSelfPunch(me.id(), body == null ? null : body.at()));
    }

    @Operation(summary = "Operator-added punch off paper proof",
            description = "Operator records a missed punch with a free-text reason. Bypasses the " +
                    "WEB 2/day cap; standard dedup on (employee, exact instant) still applies. Requires `attendance.operator`.")
    @PostMapping("/operator/punch")
    @RequirePermission("attendance.operator")
    @ResponseStatus(HttpStatus.CREATED)
    public PunchDto operatorPunch(@Valid @RequestBody OperatorPunchRequest req) {
        return PunchDtoMapper.of(punches.recordOperatorPunch(req.employeeId(), req.punchedAt(), req.reason(), req.direction()));
    }

    @Operation(summary = "Auto attendance over a date range (operator)",
            description = "Fills one employee's attendance from `from` to `to` (max 62 days): an IN punch " +
                    "at `inTime` and an OUT at `outTime` per day, pinned as the record's check-in / " +
                    "check-out, with the narration \"Auto attendance applied through operator\" on " +
                    "the record notes and punch reason. One transaction. Sundays / holidays (unless " +
                    "`includeOffDays`), full-day approved leave, locked periods and days that already " +
                    "have punches (unless `replaceExisting`) are skipped and listed with a reason. " +
                    "Future days are filled too. Requires `attendance.operator`.")
    @PostMapping("/operator/auto-attendance")
    @RequirePermission("attendance.operator")
    public com.forward.hrms.attendance.dto.response.AutoAttendanceResponse autoAttendance(
            @Valid @RequestBody AutoAttendanceRequest req) {
        return autoAttendance.apply(req);
    }

    @Operation(summary = "Operator-added punches in bulk",
            description = "End-of-shift / paper-form bulk insert. Up to 100 rows per call. " +
                    "Whole batch is one transaction — any failure rolls back the lot. Each " +
                    "row dedups against same-instant duplicates and produces an audit row. " +
                    "Requires `attendance.operator`.")
    @ApiResponse(responseCode = "201", description = "All punches recorded.")
    @ApiResponse(responseCode = "409", description = "PUNCH_DUPLICATE on a same-instant collision; whole batch rolled back.")
    @ApiResponse(responseCode = "422", description = "PUNCH_FUTURE on any row in the batch; whole batch rolled back.")
    @PostMapping("/operator/punches/bulk")
    @RequirePermission("attendance.operator")
    @ResponseStatus(HttpStatus.CREATED)
    public com.forward.hrms.attendance.dto.response.BulkPunchResponse operatorPunchesBulk(
            @Valid @RequestBody BulkOperatorPunchRequest req) {
        List<PunchService.OperatorRow> rows = req.punches().stream()
                .map(r -> new PunchService.OperatorRow(r.employeeId(), r.punchedAt(), r.reason()))
                .toList();
        return punches.recordOperatorPunchesBulk(rows);
    }

    @Operation(summary = "Amend an existing punch (operator)",
            description = "Updates `punchedAt` on a single punch and rewrites the day's attendance " +
                    "record. If the new instant moves the work-date (pre-shift window), both old " +
                    "and new days are recomputed. The before/after snapshot lands on the audit " +
                    "timeline. Requires `attendance.operator`.")
    @ApiResponse(responseCode = "200", description = "Punch amended.")
    @ApiResponse(responseCode = "404", description = "PUNCH_NOT_FOUND.")
    @ApiResponse(responseCode = "409", description = "PUNCH_DUPLICATE — another punch already exists for the new instant.")
    @ApiResponse(responseCode = "422", description = "PUNCH_FUTURE — new instant is in the future.")
    @PatchMapping("/punches/{id}")
    @RequirePermission("attendance.operator")
    public PunchDto amendPunch(@PathVariable long id, @Valid @RequestBody AmendPunchRequest req) {
        return PunchDtoMapper.of(punches.amendPunch(id, req.punchedAt(), req.reason()));
    }

    @Operation(summary = "Delete a punch (operator)",
            description = "Hard-delete with a mandatory audit reason; recomputes the day. The audit " +
                    "event is the trail. Requires `attendance.operator`.")
    @ApiResponse(responseCode = "204", description = "Punch deleted.")
    @ApiResponse(responseCode = "404", description = "PUNCH_NOT_FOUND.")
    @PostMapping("/punches/{id}/delete")
    @RequirePermission("attendance.operator")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deletePunch(@PathVariable long id, @Valid @RequestBody DeletePunchRequest req) {
        punches.deletePunch(id, req.reason());
    }

    @Operation(summary = "Bulk biometric ingest",
            description = "Called by attendance-poller. Each punch carries `(employeeCode, deviceId, " +
                    "punchedAt)` — the device's user_id field IS the employee_code. The service " +
                    "resolves to employee_id via attendance_employees, drops same-time duplicates " +
                    "silently, and triggers attendance_records re-derivation in the same " +
                    "transaction. Punches whose employee_code has no snapshot row come back in " +
                    "`skipped[]` with reason `UNKNOWN_DEVICE_USER` and are parked in the " +
                    "unmatched-replay queue. Requires `attendance.poller`.")
    @PostMapping("/punches/ingest")
    @RequirePermission("attendance.poller")
    public IngestPunchResult ingest(@Valid @RequestBody BiometricBatchRequest req) {
        List<PunchService.BiometricRow> rows = req.punches().stream()
                .map(p -> new PunchService.BiometricRow(p.employeeCode(), p.punchedAt(), p.deviceId()))
                .toList();
        return punches.ingestBiometricBatch(rows);
    }

    @Operation(summary = "My monthly attendance",
            description = "Per-day rows for the calling employee for the supplied calendar month. Requires `attendance.read`.")
    @GetMapping("/me")
    @RequirePermission("attendance.read")
    public List<AttendanceRecordDto> me(
            @Parameter(description = "YYYY-MM, defaults to current month.")
            @RequestParam(required = false) String month) {
        EmployeeClient.EmployeeView me = caller.requireEmployee();
        YearMonth ym = month == null ? YearMonth.now() : YearMonth.parse(month);
        return query.month(me.id(), ym);
    }

    @Operation(summary = "My punches for a specific day",
            description = "Requires `attendance.read`.")
    @GetMapping("/me/punches")
    @RequirePermission("attendance.read")
    public List<PunchDto> myPunches(@RequestParam LocalDate date) {
        EmployeeClient.EmployeeView me = caller.requireEmployee();
        return query.punchesOn(me.id(), date);
    }

    @Operation(summary = "Per-employee today (HR / manager spot-check)",
            description = "Returns the employee's current work date's attendance projection — the shift " +
                    "window decides it, so a night-shift worker after midnight shows last night's record. " +
                    "Always 200: when no punches and no nightly " +
                    "close have produced a record yet, the response is `{ state: NOT_PUNCHED, record: null }`. " +
                    "Requires `attendance.read`.")
    @ApiResponse(responseCode = "200", description = "Today's projection (record may be null when state == NOT_PUNCHED).")
    @GetMapping("/employee/{id}/today")
    @RequirePermission("attendance.read")
    public com.forward.hrms.attendance.dto.response.EmployeeTodayDto employeeToday(@PathVariable long id) {
        return query.day(id, punches.resolveWorkDate(id, java.time.LocalDateTime.now()))
                .map(com.forward.hrms.attendance.dto.response.EmployeeTodayDto::present)
                .orElseGet(com.forward.hrms.attendance.dto.response.EmployeeTodayDto::notPunched);
    }

    @Operation(summary = "Per-employee daily attendance over a range (HR / manager)",
            description = "Requires `attendance.read`.")
    @GetMapping("/employee/{id}")
    @RequirePermission("attendance.read")
    public List<AttendanceRecordDto> forEmployee(
            @PathVariable long id,
            @RequestParam LocalDate from, @RequestParam LocalDate to) {
        return query.range(id, from, to);
    }

    @Operation(summary = "Batch single-date lookup (manager / HR)",
            description = "Returns a list of attendance records for the given employees on the " +
                    "given date — drives the BFF \"team today\" composite without N+1 calls. " +
                    "Caller buckets the result by `employeeId`. Cap 200. " +
                    "`date` defaults to today (server-local). Requires `attendance.read`.")
    @GetMapping("/employees")
    @RequirePermission("attendance.read")
    public List<AttendanceRecordDto> employeesOnDate(
            @Parameter(description = "Employee ids (comma-separated or repeated).")
            @RequestParam java.util.Set<Long> ids,
            @Parameter(description = "Defaults to today.")
            @RequestParam(required = false)
            @org.springframework.format.annotation.DateTimeFormat(iso = org.springframework.format.annotation.DateTimeFormat.ISO.DATE)
            LocalDate date) {
        if (ids.size() > 200) {
            throw new com.forward.hrms.commons.core.error.UnprocessableError(
                    com.forward.hrms.attendance.enums.AttendanceErrorCode.ATTENDANCE_NOT_FOUND,
                    "ids batch capped at 200; got " + ids.size());
        }
        LocalDate on = date == null ? LocalDate.now() : date;
        return new java.util.ArrayList<>(query.dayBatch(ids, on).values());
    }

    @Operation(summary = "Batch payroll snapshot (per-employee, single period)",
            description = "Aggregates a per-employee payroll snapshot (lopDays, daysPresent, " +
                    "otHours, workingDays, paidDays, absentDays) for the given employees over the " +
                    "given period. Replaces the per-employee `/attendance/employee/{id}` round-trip " +
                    "loop in payroll's compute. Cap 500 (matches payroll's largest run size today). " +
                    "Requires `attendance.read`.")
    @PostMapping("/snapshots")
    @RequirePermission("attendance.read")
    public java.util.Map<Long, com.forward.hrms.attendance.dto.response.AttendanceSnapshotDto> snapshots(
            @RequestBody @Valid com.forward.hrms.attendance.dto.request.SnapshotsRequest req) {
        if (req.employeeIds() != null && req.employeeIds().size() > 500) {
            throw new com.forward.hrms.commons.core.error.UnprocessableError(
                    com.forward.hrms.attendance.enums.AttendanceErrorCode.ATTENDANCE_NOT_FOUND,
                    "snapshots batch capped at 500; got " + req.employeeIds().size());
        }
        return query.snapshots(req.employeeIds(), req.from(), req.to());
    }

    @Operation(summary = "Per-employee punches over a range (HR / manager)",
            description = "Requires `attendance.read`.")
    @GetMapping("/employee/{id}/punches")
    @RequirePermission("attendance.read")
    public List<PunchDto> punchesForEmployee(
            @PathVariable long id,
            @RequestParam LocalDate from, @RequestParam LocalDate to) {
        return query.punchesRange(id, from, to);
    }

    @Operation(summary = "Per-employee canteen usage over a range",
            description = "Aggregates CANTEEN-typed device events for the employee between " +
                    "`from` and `to` (inclusive). Returns `days` (distinct calendar dates) and " +
                    "`weeks` (distinct 7-day chunks counted from the 1st of the month: days " +
                    "1–7, 8–14, 15–21, 22–28, 29–end). Payroll's compute consumes this to apply " +
                    "the grade-conditional canteen deduction. Requires `attendance.read`.")
    @GetMapping("/employee/{id}/canteen-usage")
    @RequirePermission("attendance.read")
    public com.forward.hrms.attendance.dto.response.CanteenUsageDto canteenUsage(
            @PathVariable long id,
            @RequestParam LocalDate from, @RequestParam LocalDate to) {
        return query.canteenUsage(id, from, to);
    }

    @Operation(summary = "Per-employee heatmap rollup",
            description = "Per-day status + late variance for a calendar month — server-side " +
                    "rollup that drives the dashboard heatmap (D-S5). Falls back to WEEKEND on " +
                    "Sunday and ABSENT on weekdays for days with no record. Requires `attendance.read`.")
    @GetMapping("/employee/{id}/heatmap")
    @RequirePermission("attendance.read")
    public EmployeeHeatmapDto heatmapForEmployee(
            @PathVariable long id,
            @Parameter(description = "YYYY-MM, defaults to current month.")
            @RequestParam(required = false) String month) {
        YearMonth ym = month == null ? YearMonth.now() : YearMonth.parse(month);
        return query.heatmap(id, ym);
    }

    @Operation(summary = "Org-wide attendance records (HR / admin / operator)",
            description = "Paginated daily-record list across the company. All filters are " +
                    "optional; when no date filter is supplied the response is scoped to today " +
                    "(server-local). `date` is a shortcut for `from=to=date`. `employeeId` wins " +
                    "over `employeeCode`; `departmentId`/`sectionId` are pre-resolved against " +
                    "employee-service. Default sort `attendanceDate,desc;employeeId,asc`. Default " +
                    "page size 50 (max 500). Requires `attendance.admin` OR `attendance.operator` " +
                    "— covers HR, admin, and the attendance-operator persona; managers and plain " +
                    "employees use the per-employee `/me` and `/employee/{id}` endpoints instead.")
    @ApiResponse(responseCode = "200", description = "Page of attendance records.")
    @GetMapping("/records")
    @RequirePermission({"attendance.admin", "attendance.operator"})
    public PageResponse<AttendanceRecordDto> records(
            @Parameter(description = "Single-day shortcut. Wins over from/to.")
            @RequestParam(required = false)
            @org.springframework.format.annotation.DateTimeFormat(iso = org.springframework.format.annotation.DateTimeFormat.ISO.DATE)
            LocalDate date,
            @Parameter(description = "Inclusive lower bound. Defaults to today when neither date nor from/to supplied.")
            @RequestParam(required = false)
            @org.springframework.format.annotation.DateTimeFormat(iso = org.springframework.format.annotation.DateTimeFormat.ISO.DATE)
            LocalDate from,
            @Parameter(description = "Inclusive upper bound. Defaults to today when neither date nor from/to supplied.")
            @RequestParam(required = false)
            @org.springframework.format.annotation.DateTimeFormat(iso = org.springframework.format.annotation.DateTimeFormat.ISO.DATE)
            LocalDate to,
            @Parameter(description = "Filter by employee id. Wins over employeeCode.")
            @RequestParam(required = false) Long employeeId,
            @Parameter(description = "Filter by employee code (e.g. FS-2026-0042).")
            @RequestParam(required = false) String employeeCode,
            @Parameter(description = "Filter by department id (resolved via employee-service).")
            @RequestParam(required = false) Long departmentId,
            @Parameter(description = "Filter by section id (resolved via employee-service).")
            @RequestParam(required = false) Long sectionId,
            @Parameter(description = "Filter by attendance status.")
            @RequestParam(required = false) AttendanceStatus status,
            @Parameter(description = "When true, return only rows with lateMinutes > 0.")
            @RequestParam(required = false) Boolean lateOnly,
            @Parameter(description = "When true, return only rows with a check-in but no check-out " +
                    "(operator dashboard: \"who forgot to punch out\").")
            @RequestParam(required = false) Boolean missingCheckout,
            @Parameter(hidden = true) org.springframework.data.domain.Pageable pageable) {
        return listing.listRecords(new AttendanceListingService.RecordFilter(
                        date, from, to, employeeId, employeeCode, departmentId, sectionId, status,
                        lateOnly, missingCheckout, null),
                PageableDefaults.apply(pageable, "attendanceDate"));
    }

    @Operation(summary = "Org-wide punches (HR / admin / operator)",
            description = "Paginated raw-punch log across the company — primary view for the " +
                    "attendance-operator's daily reconciliation. All filters are optional; " +
                    "default scope is today. `date` is a shortcut for `from=to=date`. " +
                    "`employeeId` wins over `employeeCode`; `departmentId`/`sectionId` are pre-" +
                    "resolved against employee-service. Default sort `punchedAt,desc`. Default " +
                    "page size 50 (max 500). Requires `attendance.admin` OR `attendance.operator`.")
    @ApiResponse(responseCode = "200", description = "Page of punches.")
    @GetMapping("/punches")
    @RequirePermission({"attendance.admin", "attendance.operator"})
    public PageResponse<PunchDto> punchesList(
            @Parameter(description = "Single-day shortcut. Wins over from/to.")
            @RequestParam(required = false)
            @org.springframework.format.annotation.DateTimeFormat(iso = org.springframework.format.annotation.DateTimeFormat.ISO.DATE)
            LocalDate date,
            @Parameter(description = "Inclusive lower bound. Defaults to today when neither date nor from/to supplied.")
            @RequestParam(required = false)
            @org.springframework.format.annotation.DateTimeFormat(iso = org.springframework.format.annotation.DateTimeFormat.ISO.DATE)
            LocalDate from,
            @Parameter(description = "Inclusive upper bound. Defaults to today when neither date nor from/to supplied.")
            @RequestParam(required = false)
            @org.springframework.format.annotation.DateTimeFormat(iso = org.springframework.format.annotation.DateTimeFormat.ISO.DATE)
            LocalDate to,
            @Parameter(description = "Filter by employee id. Wins over employeeCode.")
            @RequestParam(required = false) Long employeeId,
            @Parameter(description = "Filter by employee code.")
            @RequestParam(required = false) String employeeCode,
            @Parameter(description = "Filter by department id (resolved via employee-service).")
            @RequestParam(required = false) Long departmentId,
            @Parameter(description = "Filter by section id (resolved via employee-service).")
            @RequestParam(required = false) Long sectionId,
            @Parameter(description = "Filter by punch source.")
            @RequestParam(required = false) PunchSource source,
            @Parameter(description = "Filter by attendance device id.")
            @RequestParam(required = false) Long deviceId,
            @Parameter(hidden = true) org.springframework.data.domain.Pageable pageable) {
        return listing.listPunches(new AttendanceListingService.PunchFilter(
                        date, from, to, employeeId, employeeCode, departmentId, sectionId, source, deviceId, null),
                PageableDefaults.apply(pageable, "punchedAt"));
    }

    @Operation(summary = "My heatmap (ESS)",
            description = "Per-day status + late variance for the calling employee. Same shape " +
                    "as the per-id endpoint; this one is open to any authenticated employee. " +
                    "Requires `attendance.read`.")
    @GetMapping("/me/heatmap")
    @RequirePermission("attendance.read")
    public EmployeeHeatmapDto myHeatmap(
            @Parameter(description = "YYYY-MM, defaults to current month.")
            @RequestParam(required = false) String month) {
        EmployeeClient.EmployeeView me = caller.requireEmployee();
        YearMonth ym = month == null ? YearMonth.now() : YearMonth.parse(month);
        return query.heatmap(me.id(), ym);
    }

    // New endpoint to get the required information for termination process
    @Operation(summary = "Termination attendance summary",
            description = "Attendance and overtime summary for final settlement."
    )
    @GetMapping("/employee/{employeeId}/termination-summary")
    @RequirePermission("attendance.read")
    public TerminationAttendanceSummaryDto terminationSummary(
            @PathVariable long employeeId,
            @RequestParam LocalDate from,
            @RequestParam LocalDate to
    ) {

        return query.terminationSummary(employeeId, from, to);
    }
}
