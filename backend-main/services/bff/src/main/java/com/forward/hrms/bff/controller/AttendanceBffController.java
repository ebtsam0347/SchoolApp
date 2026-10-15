package com.forward.hrms.bff.controller;

import com.forward.hrms.audit.dto.response.TimelinePage;
import com.forward.hrms.attendance.dto.response.AttendanceMonth;
import com.forward.hrms.bff.dto.response.TeamAttendanceToday;
import com.forward.hrms.bff.service.AttendanceBffService;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;

import lombok.RequiredArgsConstructor;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;

@Tag(name = "Attendance — Composites",
        description = "Cross-service composites that join attendance with audit (timelines) " +
                "and employee-service (manager coverage). Single-service composites moved into " +
                "attendance-service in Step 5 of docs/caching.md (today, month, records-with-employee, " +
                "punches-with-employee, operator dashboard, overtime/me/with-records, leaves/apply-context, " +
                "approvals/inbox).")
@RestController
@RequestMapping("/bff")
@RequiredArgsConstructor
public class AttendanceBffController {

    private final AttendanceBffService service;

    @Operation(summary = "Team roll-call (manager)",
            description = "One row per direct report plus any employee whose manager has delegated " +
                    "approval authority to the caller for the supplied date. Each row carries the " +
                    "report's compact summary, today's status, punch in/out, and lateness. Capped at " +
                    "500 rows — beyond that `truncated=true` and the FE drops to a filtered org page. " +
                    "Returns 403 when the caller covers nobody (no direct reports and no active " +
                    "delegation) — same contract as the manager dashboard, so the FE gates the " +
                    "\"My Team\" nav on it. The active-leave chip data is reserved for a future " +
                    "upstream addition; v1 sets `activeLeave: null` on every row.")
    @ApiResponse(responseCode = "200", description = "Roll-call rows + asOf date.")
    @ApiResponse(responseCode = "403", description = "Caller has no direct reports and holds no covering delegation.")
    @GetMapping("/attendance/team/today")
    @PreAuthorize("isAuthenticated()")
    public TeamAttendanceToday teamToday(
            @Parameter(description = "Date to evaluate. Defaults to today (Asia/Karachi).")
            @RequestParam(required = false)
            @org.springframework.format.annotation.DateTimeFormat(iso = org.springframework.format.annotation.DateTimeFormat.ISO.DATE)
            LocalDate date) {
        return service.teamToday(date);
    }

    @Operation(summary = "Per-employee month view (manager)",
            description = "Same shape as `/attendance/me/month/composite` so the FE drawer reuses " +
                    "the existing component. Caller must be the target's current manager OR hold an " +
                    "active approval delegation covering them on the requested month — 403 otherwise.")
    @ApiResponse(responseCode = "200", description = "Per-day list + month summary for the target.")
    @ApiResponse(responseCode = "403", description = "Caller does not manage this employee and holds no covering delegation.")
    @GetMapping("/attendance/team/{employeeId}/month")
    @PreAuthorize("isAuthenticated()")
    public AttendanceMonth teamMonth(
            @PathVariable long employeeId,
            @Parameter(description = "YYYY-MM, defaults to current month (Asia/Karachi).")
            @RequestParam(required = false) String month) {
        return service.teamMonth(employeeId, month);
    }

    @Operation(summary = "Approval-request timeline",
            description = "Audit feed for a single leave / short leave / overtime / regularization request, " +
                    "for the drawer Activity tab. The BFF probes the upstream request first " +
                    "(404 if it doesn't exist or the caller can't see it) and then reads the " +
                    "audit timeline. Cursor pagination via the audit-service cursor.")
    @ApiResponse(responseCode = "200", description = "Timeline page (newest first).")
    @ApiResponse(responseCode = "404", description = "Request not found / not visible.")
    @ApiResponse(responseCode = "422", description = "requestType is not one of leave, short-leave, overtime, regularization.")
    @GetMapping("/timeline/{requestType}/{id}")
    @PreAuthorize("isAuthenticated()")
    public TimelinePage requestTimeline(
            @Parameter(description = "One of: leave, short-leave, overtime, regularization.")
            @PathVariable String requestType,
            @PathVariable long id,
            @Parameter(description = "Page size (default 50, max 200).")
            @RequestParam(required = false) Integer limit,
            @Parameter(description = "Opaque cursor from a previous response's `nextCursor`.")
            @RequestParam(required = false) String cursor) {
        return service.requestTimeline(requestType, id, limit, cursor);
    }
}
