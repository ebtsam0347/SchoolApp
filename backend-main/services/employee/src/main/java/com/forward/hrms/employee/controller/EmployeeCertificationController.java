package com.forward.hrms.employee.controller;

import com.forward.hrms.commons.web.page.PageResponse;
import com.forward.hrms.commons.web.page.PageableDefaults;
import com.forward.hrms.commons.web.security.RequirePermission;
import com.forward.hrms.employee.dto.request.AddCertificationRequest;
import com.forward.hrms.employee.dto.request.UpdateCertificationRequest;
import com.forward.hrms.employee.dto.response.EmployeeCertificationDto;
import com.forward.hrms.employee.service.EmployeeCertificationService;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;

import lombok.RequiredArgsConstructor;

import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
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

import java.util.List;

@Tag(name = "Employees — Certifications",
        description = "Per-employee certifications + the global expiring queue HR uses to chase renewals. Requires `employee.read` / `employee.write`.")
@RestController
@RequiredArgsConstructor
@RequestMapping
public class EmployeeCertificationController {

    private final EmployeeCertificationService service;

    @Operation(summary = "List an employee's certifications", description = "Requires `employee.read`.")
    @GetMapping("/employees/{id}/certifications")
    @RequirePermission("employee.read")
    public List<EmployeeCertificationDto> list(@PathVariable long id) {
        return service.list(id);
    }

    @Operation(summary = "Add a certification", description = "Requires `employee.write`.")
    @ApiResponse(responseCode = "201", description = "Created.")
    @PostMapping("/employees/{id}/certifications")
    @RequirePermission("employee.write")
    @ResponseStatus(HttpStatus.CREATED)
    public EmployeeCertificationDto add(@PathVariable long id,
                                        @Valid @RequestBody AddCertificationRequest req) {
        return service.add(id, req);
    }

    @Operation(summary = "Patch a certification", description = "Requires `employee.write`.")
    @PatchMapping("/employees/{id}/certifications/{certId}")
    @RequirePermission("employee.write")
    public EmployeeCertificationDto update(@PathVariable long id, @PathVariable long certId,
                                           @Valid @RequestBody UpdateCertificationRequest req) {
        return service.update(id, certId, req);
    }

    @Operation(summary = "Delete a certification", description = "Requires `employee.write`.")
    @ApiResponse(responseCode = "204", description = "Deleted.")
    @DeleteMapping("/employees/{id}/certifications/{certId}")
    @RequirePermission("employee.write")
    public ResponseEntity<Void> delete(@PathVariable long id, @PathVariable long certId) {
        service.delete(id, certId);
        return ResponseEntity.noContent().build();
    }

    @Operation(summary = "Global expiring-cert queue",
            description = "Certs with `expiry_date <= today + days`, oldest first. By default " +
                    "already-expired rows are included so HR sees what's overdue; pass `expired=false` " +
                    "to scope to future-dated only, or `expired=true` to scope to already-expired only. " +
                    "`expiringWithin` is required unless `expired=true` (in that mode the upper bound " +
                    "is fixed at today and the days window is meaningless). `departmentId` / `sectionId` " +
                    "narrow the queue to an org slice via the cert's owning employee. Requires `employee.read`.")
    @GetMapping("/certifications")
    @RequirePermission("employee.read")
    public PageResponse<EmployeeCertificationDto> expiringWithin(
            @Parameter(description = "Days from today. Required unless `expired=true`.")
            @RequestParam(required = false) @Min(0) Integer expiringWithin,
            @Parameter(description = "Narrow to certs whose owning employee is in this department.")
            @RequestParam(required = false) Long departmentId,
            @Parameter(description = "Narrow to certs whose owning employee is in this section.")
            @RequestParam(required = false) Long sectionId,
            @Parameter(description = "true = already-expired only; false = future only; null = both.")
            @RequestParam(required = false) Boolean expired,
            @Parameter(hidden = true) Pageable pageable) {
        return service.expiringWithin(expiringWithin, departmentId, sectionId, expired,
                PageableDefaults.apply(pageable, "expiryDate"));
    }
}
