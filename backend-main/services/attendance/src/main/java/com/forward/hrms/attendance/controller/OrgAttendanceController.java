package com.forward.hrms.attendance.controller;

import com.forward.hrms.attendance.dto.response.NotCheckedInDto;
import com.forward.hrms.attendance.dto.response.OrgAttendanceDayDto;
import com.forward.hrms.attendance.dto.response.OrgPulseDto;
import com.forward.hrms.attendance.service.NotCheckedInQueryService;
import com.forward.hrms.attendance.service.OrgAttendanceQueryService;
import com.forward.hrms.commons.web.page.PageResponse;
import com.forward.hrms.commons.web.security.RequirePermission;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;

import lombok.RequiredArgsConstructor;

import org.springframework.data.domain.Pageable;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

@Tag(name = "Attendance — Org rollups",
        description = "Org-wide attendance aggregates. Two queries — one for a single date " +
                "(HR dashboard's `OrgPulse`) and one for a range (the 30d trend chart). Both " +
                "require `attendance.read` (HR scope).")
@RestController
@RequestMapping("/attendance/org")
@RequiredArgsConstructor
public class OrgAttendanceController {

    private final OrgAttendanceQueryService service;
    private final NotCheckedInQueryService notCheckedInService;

    @Operation(summary = "Org-wide attendance pulse for a date",
            description = "Single SQL aggregate over `attendance_records` grouped by status. " +
                    "Defaults to today (server-local date). Requires `attendance.read`.")
    @ApiResponse(responseCode = "200", description = "Status counts for the supplied date.")
    @GetMapping("/pulse")
    @RequirePermission("attendance.read")
    public OrgPulseDto pulse(
            @Parameter(description = "Date to roll up (server-local). Defaults to today.")
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate asOf) {
        return service.pulse(asOf == null ? LocalDate.now() : asOf);
    }

    @Operation(summary = "Org-wide attendance volume per day over a range",
            description = "Per-day status counts. Days with no records (rare — usually means " +
                    "the nightly close hasn't run) are absent from the response, not zero rows. " +
                    "Requires `attendance.read`.")
    @GetMapping("/attendance")
    @RequirePermission("attendance.read")
    public List<OrgAttendanceDayDto> dailyTrend(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        return service.dailyTrend(from, to);
    }

    @Operation(summary = "Who hasn't checked in yet (live)",
            description = "Active employees whose shift-start + grace has elapsed today without a " +
                    "check-in punch, most overdue first. Weekend / holiday / approved-leave and " +
                    "not-yet-due shifts are excluded; the list shrinks as people punch in and clears " +
                    "itself — nothing is persisted (distinct from the nightly ABSENT status). " +
                    "Paginated (default 20, max 200); always sorted by `minutesOverdue` descending. " +
                    "Optional `employeeCode` keeps employees whose code contains it (case-insensitive). " +
                    "Requires `attendance.read`.")
    @ApiResponse(responseCode = "200", description = "Not-checked-in roster as of now.")
    @GetMapping("/not-checked-in")
    @RequirePermission("attendance.read")
    public PageResponse<NotCheckedInDto> notCheckedIn(
            @Parameter(description = "Employee code, or part of one (case-insensitive).")
            @RequestParam(required = false) String employeeCode,
            Pageable pageable) {
        return notCheckedInService.notCheckedIn(LocalDateTime.now(), employeeCode, pageable);
    }
}
