package com.forward.hrms.attendance.controller;

import com.forward.hrms.attendance.dto.response.AttendancePeriodLockDto;
import com.forward.hrms.attendance.service.AttendancePeriodLockService;
import com.forward.hrms.commons.web.security.RequirePermission;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;

import lombok.RequiredArgsConstructor;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@Tag(name = "Attendance Periods",
        description = "Period-level lock used by payroll's lock-attendance step. The presence of a lock " +
                "row IS the lock — POST /lock is idempotent, POST /unlock removes it. Listing requires " +
                "`attendance.read`; mutating requires `attendance.admin`.")
@RestController
@RequestMapping("/attendance/periods")
@RequiredArgsConstructor
public class AttendancePeriodController {

    private final AttendancePeriodLockService service;

    @Operation(summary = "List locked periods in a year",
            description = "Returns one row per locked (year, month). Requires `attendance.read`.")
    @GetMapping("/locks")
    @RequirePermission("attendance.read")
    public List<AttendancePeriodLockDto> list(@RequestParam int year) {
        return service.listForYear(year);
    }

    @Operation(summary = "Lock a payroll period",
            description = "Idempotent — re-locking returns the existing lock without error. " +
                    "Requires `attendance.admin`.")
    @PostMapping("/lock")
    @RequirePermission("attendance.admin")
    public AttendancePeriodLockDto lock(@RequestParam int year, @RequestParam int month) {
        return service.lock(year, month);
    }

    @Operation(summary = "Unlock a payroll period",
            description = "404 if the period is not currently locked. Requires `attendance.admin`.")
    @PostMapping("/unlock")
    @RequirePermission("attendance.admin")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void unlock(@RequestParam int year, @RequestParam int month) {
        service.unlock(year, month);
    }
}
