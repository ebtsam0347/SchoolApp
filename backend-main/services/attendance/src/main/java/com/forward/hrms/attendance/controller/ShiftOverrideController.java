package com.forward.hrms.attendance.controller;

import com.forward.hrms.attendance.dto.request.CreateShiftOverrideRequest;
import com.forward.hrms.attendance.dto.request.UpdateShiftOverrideRequest;
import com.forward.hrms.attendance.dto.response.ShiftOverrideDto;
import com.forward.hrms.attendance.service.ShiftOverrideService;
import com.forward.hrms.commons.web.security.RequirePermission;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;

import jakarta.validation.Valid;

import lombok.RequiredArgsConstructor;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.util.List;

@Tag(name = "Shift overrides",
        description = "Operator-driven per-employee overrides over a date range. Triggers same-tx " +
                "recompute of attendance_records snapshots + minutes for every date in the range. " +
                "Reads require `attendance.read`; writes require `attendance.operator` " +
                "(held by ATTENDANCE_OPERATOR, HR, ADMIN). Every write emits an audit event onto " +
                "the per-entity timeline.")
@RestController
@RequestMapping("/attendance/shift-overrides")
@RequiredArgsConstructor
public class ShiftOverrideController {

    private final ShiftOverrideService service;

    @Operation(summary = "Create overrides for a list of employees over a date range",
            description = "Requires `attendance.operator`.")
    @PostMapping
    @RequirePermission("attendance.operator")
    @ResponseStatus(HttpStatus.CREATED)
    public List<ShiftOverrideDto> create(@Valid @RequestBody CreateShiftOverrideRequest req) {
        return service.create(req);
    }

    @Operation(summary = "List overrides covering a date or for an employee over a range",
            description = "Requires `attendance.read`.")
    @GetMapping
    @RequirePermission("attendance.read")
    public List<ShiftOverrideDto> list(
            @RequestParam(required = false) LocalDate date,
            @RequestParam(required = false) Long employeeId,
            @RequestParam(required = false) LocalDate from,
            @RequestParam(required = false) LocalDate to) {
        if (date != null) return service.listForDate(date);
        if (employeeId != null && from != null && to != null) {
            return service.listForEmployee(employeeId, from, to);
        }
        throw new com.forward.hrms.commons.core.error.UnprocessableError(
                com.forward.hrms.attendance.enums.AttendanceErrorCode.OVERRIDE_RANGE_INVALID,
                "supply either ?date= or ?employeeId=&from=&to=");
    }

    @Operation(summary = "Patch an override (re-triggers recompute over the union of old + new ranges)",
            description = "Requires `attendance.operator`.")
    @PatchMapping("/{id}")
    @RequirePermission("attendance.operator")
    public ShiftOverrideDto update(@PathVariable long id, @Valid @RequestBody UpdateShiftOverrideRequest req) {
        return service.update(id, req);
    }

    @Operation(summary = "Delete an override (reverts every date in the range)",
            description = "Requires `attendance.operator`.")
    @DeleteMapping("/{id}")
    @RequirePermission("attendance.operator")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable long id) {
        service.delete(id);
    }
}
