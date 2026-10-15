package com.forward.hrms.employee.controller;

import com.forward.hrms.commons.web.security.RequirePermission;
import com.forward.hrms.employee.dto.request.LiftSuspensionRequest;
import com.forward.hrms.employee.dto.request.ResignRequest;
import com.forward.hrms.employee.dto.request.SuspendRequest;
import com.forward.hrms.employee.dto.request.TerminateRequest;
import com.forward.hrms.employee.dto.request.WithdrawExitRequest;
import com.forward.hrms.employee.dto.response.ExitDto;
import com.forward.hrms.employee.dto.response.SuspensionDto;
import com.forward.hrms.employee.service.ExitService;
import com.forward.hrms.employee.service.SuspensionService;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;

import jakarta.validation.Valid;

import lombok.RequiredArgsConstructor;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@Tag(name = "Employees — Lifecycle",
        description = "Suspensions and exits (resignation / termination / retirement). " +
                "Auth-side disable / re-enable is driven by `employee.suspended` / " +
                "`employee.suspension_lifted` / `employee.terminated` events. " +
                "Requires `employee.read` / `employee.write`.")
@RestController
@RequestMapping("/employees")
@RequiredArgsConstructor
public class LifecycleController {

    private final SuspensionService suspensions;
    private final ExitService exits;

    // -------- suspensions --------

    @Operation(summary = "Suspend an employee",
            description = "Allowed when employment_status is ACTIVE or ON_NOTICE and the employee " +
                    "has no active suspension. Sets status to SUSPENDED and emits employee.suspended. Requires `employee.write`.")
    @ApiResponse(responseCode = "409", description = "Already suspended / not in a suspendable state.")
    @PostMapping("/{id}/suspend")
    @RequirePermission("employee.write")
    public SuspensionDto suspend(@PathVariable long id, @Valid @RequestBody SuspendRequest req) {
        return suspensions.suspend(id, req);
    }

    @Operation(summary = "Lift the active suspension",
            description = "Restores employment_status to ON_NOTICE when there's a PENDING exit row, " +
                    "ACTIVE otherwise. Emits employee.suspension_lifted (auth re-enables the user). Requires `employee.write`.")
    @ApiResponse(responseCode = "409", description = "No active suspension.")
    @PostMapping("/{id}/lift-suspension")
    @RequirePermission("employee.write")
    public SuspensionDto liftSuspension(@PathVariable long id,
                                        @Valid @RequestBody LiftSuspensionRequest req) {
        return suspensions.lift(id, req);
    }

    @Operation(summary = "List suspension history (current + historical)", description = "Requires `employee.read`.")
    @GetMapping("/{id}/suspensions")
    @RequirePermission("employee.read")
    public List<SuspensionDto> listSuspensions(@PathVariable long id) {
        return suspensions.list(id);
    }

    // -------- exits --------

    @Operation(summary = "Submit a resignation",
            description = "Inserts a PENDING exit row, flips employment_status to ON_NOTICE, " +
                    "and emits employee.resignation_submitted. The user keeps full ESS through the " +
                    "notice period; auth disables only after the daily completion job emits " +
                    "employee.terminated on the last working day. Direct reports must be reassigned. Requires `employee.write`.")
    @ApiResponse(responseCode = "409", description = "Pending exit already exists / direct reports unaccounted for.")
    @ApiResponse(responseCode = "422", description = "lastWorkingDay must be in the future.")
    @PostMapping("/{id}/resign")
    @RequirePermission("employee.write")
    public ExitDto resign(@PathVariable long id, @Valid @RequestBody ResignRequest req) {
        return exits.resign(id, req);
    }

    @Operation(summary = "Terminate immediately",
            description = "Runs the termination saga right away: marks TERMINATED, lifts any active " +
                    "suspension, closes the history row, and emits employee.terminated. Requires `employee.write`.")
    @ApiResponse(responseCode = "409", description = "Already terminated / direct reports unaccounted for.")
    @PostMapping("/{id}/terminate")
    @RequirePermission("employee.write")
    public ExitDto terminate(@PathVariable long id, @Valid @RequestBody TerminateRequest req) {
        return exits.terminate(id, req);
    }

    @Operation(summary = "Withdraw a pending resignation",
            description = "Allowed only while status=PENDING. Restores employment_status to ACTIVE " +
                    "(unless suspended) and emits employee.resignation_withdrawn. Requires `employee.write`.")
    @ApiResponse(responseCode = "404", description = "No exit row.")
    @ApiResponse(responseCode = "409", description = "Exit not in PENDING state.")
    @PostMapping("/{id}/withdraw-exit")
    @RequirePermission("employee.write")
    public ExitDto withdraw(@PathVariable long id, @Valid @RequestBody WithdrawExitRequest req) {
        return exits.withdraw(id, req);
    }

    @Operation(summary = "Get the current exit row for this employee", description = "Requires `employee.read`.")
    @ApiResponse(responseCode = "404", description = "No exit row.")
    @GetMapping("/{id}/exit")
    @RequirePermission("employee.read")
    public ExitDto exit(@PathVariable long id) {
        return exits.get(id);
    }
}
