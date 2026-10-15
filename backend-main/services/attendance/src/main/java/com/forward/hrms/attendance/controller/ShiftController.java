package com.forward.hrms.attendance.controller;

import com.forward.hrms.attendance.dto.request.AssignShiftRequest;
import com.forward.hrms.attendance.dto.request.CreateShiftRequest;
import com.forward.hrms.attendance.dto.request.ReorderEntryRequest;
import com.forward.hrms.attendance.dto.response.EmployeeShiftDto;
import com.forward.hrms.attendance.dto.response.ShiftDto;
import com.forward.hrms.attendance.service.ShiftService;
import com.forward.hrms.commons.web.security.RequirePermission;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;

import jakarta.validation.Valid;

import lombok.RequiredArgsConstructor;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@Tag(name = "Shifts", description = "Shift catalog + per-employee assignment. Requires `attendance.read` / `attendance.admin`.")
@RestController
@RequiredArgsConstructor
public class ShiftController {

    private final ShiftService shifts;

    @Operation(summary = "List active shifts",
            description = "Requires `attendance.read`.")
    @GetMapping("/shifts")
    @RequirePermission("attendance.read")
    public List<ShiftDto> list() {
        return shifts.list();
    }

    @Operation(summary = "Create a shift (HR)",
            description = "Requires `attendance.admin`.")
    @PostMapping("/shifts")
    @RequirePermission("attendance.admin")
    @ResponseStatus(HttpStatus.CREATED)
    public ShiftDto create(@Valid @RequestBody CreateShiftRequest req) {
        return shifts.create(req);
    }

    @Operation(summary = "Bulk-reorder shifts",
            description = "Applies the supplied `displayOrder` to each listed shift in a single " +
                    "transaction. Returns the active catalog. Requires `attendance.admin`.")
    @PatchMapping("/shifts/reorder")
    @RequirePermission("attendance.admin")
    public List<ShiftDto> reorder(@Valid @RequestBody List<ReorderEntryRequest> entries) {
        return shifts.reorder(entries);
    }

    @Operation(summary = "Assign a shift to an employee",
            description = "Routed under /shifts so the gateway can keep /employees/** scoped to employee-service. Requires `attendance.admin`.")
    @PostMapping("/shifts/employees/{id}")
    @RequirePermission("attendance.admin")
    @ResponseStatus(HttpStatus.CREATED)
    public void assign(@PathVariable long id, @Valid @RequestBody AssignShiftRequest req) {
        shifts.assign(id, req);
    }

    @Operation(summary = "Get an employee's shift assignments",
            description = "Returns the employee's shift-assignment history newest-first by " +
                    "`effectiveFrom`. The first row whose range covers today is the active " +
                    "assignment; rows with `effectiveTo` set are closed history. Requires `attendance.read`.")
    @GetMapping("/shifts/employees/{id}")
    @RequirePermission("attendance.read")
    public List<EmployeeShiftDto> assignments(@PathVariable long id) {
        return shifts.assignmentsFor(id);
    }
}
