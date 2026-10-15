package com.forward.hrms.attendance.controller;

import com.forward.hrms.attendance.enums.RegularizationStatus;
import com.forward.hrms.attendance.service.AttendanceReportService;
import com.forward.hrms.attendance.service.LeaveReportService;
import com.forward.hrms.commons.web.security.RequirePermission;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;

import lombok.RequiredArgsConstructor;

import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.StreamingResponseBody;

import java.time.LocalDate;

/**
 * Standard reports owned by attendance-service. All sync XLSX streams.
 * Async substrate (report_jobs) lives in payroll today; if attendance
 * needs an async report later we'll mirror that pattern under
 * {@code attendance.report_jobs}.
 */
@Tag(name = "Attendance reports",
        description = "Standard exportable reports owned by attendance-service. " +
                "Requires `attendance.reports.run` (or `attendance.leaves.reports.run` for leave reports).")
@RestController
@RequestMapping("/reports")
@RequiredArgsConstructor
public class ReportsController {

    private static final String XLSX_TYPE = "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet";

    private final AttendanceReportService attendance;
    private final LeaveReportService leaves;

    // ---------- Attendance ----------

    @Operation(summary = "Daily attendance sheet — Excel",
            description = "Every employee's record for the named date. Requires `attendance.reports.run`.")
    @GetMapping(value = "/attendance/daily", produces = XLSX_TYPE)
    @RequirePermission("attendance.reports.run")
    public ResponseEntity<StreamingResponseBody> daily(
            @Parameter(description = "Date (yyyy-mm-dd).")
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date) {
        return xlsx("attendance-%s.xlsx".formatted(date),
                out -> attendance.writeDailyXlsx(out, date));
    }

    @Operation(summary = "Monthly attendance summary — Excel",
            description = "Per-employee status counts for the month. Requires `attendance.reports.run`.")
    @GetMapping(value = "/attendance/monthly", produces = XLSX_TYPE)
    @RequirePermission("attendance.reports.run")
    public ResponseEntity<StreamingResponseBody> monthly(@RequestParam int year, @RequestParam int month) {
        return xlsx("attendance-monthly-%d-%02d.xlsx".formatted(year, month),
                out -> attendance.writeMonthlySummaryXlsx(out, year, month));
    }

    @Operation(summary = "Late comers — Excel",
            description = "Aggregated late-day counts and totals per employee within the date range, " +
                    "ranked by frequency. Requires `attendance.reports.run`.")
    @GetMapping(value = "/attendance/late-comers", produces = XLSX_TYPE)
    @RequirePermission("attendance.reports.run")
    public ResponseEntity<StreamingResponseBody> lateComers(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        return xlsx("late-comers-%s_to_%s.xlsx".formatted(from, to),
                out -> attendance.writeLateComersXlsx(out, from, to));
    }

    @Operation(summary = "Absentee log — Excel",
            description = "Every ABSENT record in the range. Requires `attendance.reports.run`.")
    @GetMapping(value = "/attendance/absentee-log", produces = XLSX_TYPE)
    @RequirePermission("attendance.reports.run")
    public ResponseEntity<StreamingResponseBody> absenteeLog(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        return xlsx("absentee-log-%s_to_%s.xlsx".formatted(from, to),
                out -> attendance.writeAbsenteeLogXlsx(out, from, to));
    }

    @Operation(summary = "OT register — Excel",
            description = "OT requests within the range, joined with the attendance record's OT minutes. " +
                    "Requires `attendance.reports.run`.")
    @GetMapping(value = "/attendance/ot-register", produces = XLSX_TYPE)
    @RequirePermission("attendance.reports.run")
    public ResponseEntity<StreamingResponseBody> otRegister(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        return xlsx("ot-register-%s_to_%s.xlsx".formatted(from, to),
                out -> attendance.writeOvertimeRegisterXlsx(out, from, to));
    }

    @Operation(summary = "Regularization log — Excel",
            description = "Regularization requests in the range. Optional `status` filter " +
                    "(SUBMITTED / APPROVED / REJECTED / WITHDRAWN). Requires `attendance.reports.run`.")
    @GetMapping(value = "/attendance/regularization-log", produces = XLSX_TYPE)
    @RequirePermission("attendance.reports.run")
    public ResponseEntity<StreamingResponseBody> regularizationLog(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @RequestParam(required = false) RegularizationStatus status) {
        return xlsx("regularization-%s_to_%s.xlsx".formatted(from, to),
                out -> attendance.writeRegularizationLogXlsx(out, from, to, status));
    }

    @Operation(summary = "Missing-punch log — Excel",
            description = "PRESENT records with at least one of {check-in, check-out} null in the range. " +
                    "Requires `attendance.reports.run`.")
    @GetMapping(value = "/attendance/missing-punch", produces = XLSX_TYPE)
    @RequirePermission("attendance.reports.run")
    public ResponseEntity<StreamingResponseBody> missingPunch(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        return xlsx("missing-punches-%s_to_%s.xlsx".formatted(from, to),
                out -> attendance.writeMissingPunchXlsx(out, from, to));
    }

    @Operation(summary = "Shift roster export — Excel",
            description = "Active shift assignments overlapping with the range. " +
                    "Requires `attendance.reports.run`.")
    @GetMapping(value = "/attendance/shift-roster", produces = XLSX_TYPE)
    @RequirePermission("attendance.reports.run")
    public ResponseEntity<StreamingResponseBody> shiftRoster(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        return xlsx("shift-roster-%s_to_%s.xlsx".formatted(from, to),
                out -> attendance.writeShiftRosterXlsx(out, from, to));
    }

    // ---------- Leaves ----------

    @Operation(summary = "Leave balance snapshot — Excel",
            description = "Per employee × leave type × allocated/used/pending/carried for the named year. " +
                    "Requires `attendance.leaves.reports.run`.")
    @GetMapping(value = "/leaves/balance", produces = XLSX_TYPE)
    @RequirePermission("attendance.leaves.reports.run")
    public ResponseEntity<StreamingResponseBody> leaveBalance(@RequestParam int year) {
        return xlsx("leave-balance-%d.xlsx".formatted(year),
                out -> leaves.writeBalanceXlsx(out, year));
    }

    @Operation(summary = "Leave taken — Excel",
            description = "Approved leave days in the date range. Requires `attendance.leaves.reports.run`.")
    @GetMapping(value = "/leaves/taken", produces = XLSX_TYPE)
    @RequirePermission("attendance.leaves.reports.run")
    public ResponseEntity<StreamingResponseBody> leaveTaken(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        return xlsx("leave-taken-%s_to_%s.xlsx".formatted(from, to),
                out -> leaves.writeTakenXlsx(out, from, to));
    }

    @Operation(summary = "Leave application log — Excel",
            description = "Every leave request in the date range with its status. " +
                    "Requires `attendance.leaves.reports.run`.")
    @GetMapping(value = "/leaves/application-log", produces = XLSX_TYPE)
    @RequirePermission("attendance.leaves.reports.run")
    public ResponseEntity<StreamingResponseBody> leaveApplicationLog(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        return xlsx("leave-application-log-%s_to_%s.xlsx".formatted(from, to),
                out -> leaves.writeApplicationLogXlsx(out, from, to));
    }

    @Operation(summary = "Year-end carry-forward forecast — Excel",
            description = "Projected carry-forward into next year given current allocations and the " +
                    "leave-type carry policy. Requires `attendance.leaves.reports.run`.")
    @GetMapping(value = "/leaves/carry-forward", produces = XLSX_TYPE)
    @RequirePermission("attendance.leaves.reports.run")
    public ResponseEntity<StreamingResponseBody> carryForward(@RequestParam int year) {
        return xlsx("leave-carry-forward-%d.xlsx".formatted(year),
                out -> leaves.writeCarryForwardXlsx(out, year));
    }

    // ---------- helper ----------

    @FunctionalInterface
    private interface XlsxBody {
        void write(java.io.OutputStream out) throws java.io.IOException;
    }

    private static ResponseEntity<StreamingResponseBody> xlsx(String filename, XlsxBody body) {
        StreamingResponseBody srb = body::write;
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + filename + "\"")
                .contentType(MediaType.parseMediaType(XLSX_TYPE))
                .body(srb);
    }
}
