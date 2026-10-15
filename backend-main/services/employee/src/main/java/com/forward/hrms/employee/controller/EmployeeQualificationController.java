package com.forward.hrms.employee.controller;

import com.forward.hrms.commons.web.security.RequirePermission;
import com.forward.hrms.employee.dto.request.AddEmployeeQualificationRequest;
import com.forward.hrms.employee.dto.request.UpdateEmployeeQualificationRequest;
import com.forward.hrms.employee.dto.response.EmployeeQualificationDto;
import com.forward.hrms.employee.dto.response.QualificationEmployeeDto;
import com.forward.hrms.employee.service.EmployeeQualificationService;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;

import jakarta.validation.Valid;

import lombok.RequiredArgsConstructor;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@Tag(name = "Employees — Qualifications",
        description = "Per-employee academic-qualification rows + reverse lookup. Requires `employee.read` / `employee.write`.")
@RestController
@RequiredArgsConstructor
@RequestMapping
public class EmployeeQualificationController {

    private final EmployeeQualificationService service;

    @Operation(summary = "List an employee's qualifications", description = "Requires `employee.read`.")
    @GetMapping("/employees/{id}/qualifications")
    @RequirePermission("employee.read")
    public List<EmployeeQualificationDto> list(@PathVariable long id) {
        return service.list(id);
    }

    @Operation(summary = "Attach a catalog qualification to an employee", description = "Requires `employee.write`.")
    @ApiResponse(responseCode = "201", description = "Created.")
    @PostMapping("/employees/{id}/qualifications")
    @RequirePermission("employee.write")
    @ResponseStatus(HttpStatus.CREATED)
    public EmployeeQualificationDto add(@PathVariable long id,
                                        @Valid @RequestBody AddEmployeeQualificationRequest req) {
        return service.add(id, req);
    }

    @Operation(summary = "Patch an employee qualification row", description = "Requires `employee.write`.")
    @PatchMapping("/employees/{id}/qualifications/{empQualId}")
    @RequirePermission("employee.write")
    public EmployeeQualificationDto update(@PathVariable long id, @PathVariable long empQualId,
                                           @Valid @RequestBody UpdateEmployeeQualificationRequest req) {
        return service.update(id, empQualId, req);
    }

    @Operation(summary = "Detach a qualification from an employee", description = "Requires `employee.write`.")
    @ApiResponse(responseCode = "204", description = "Removed.")
    @DeleteMapping("/employees/{id}/qualifications/{empQualId}")
    @RequirePermission("employee.write")
    public ResponseEntity<Void> delete(@PathVariable long id, @PathVariable long empQualId) {
        service.delete(id, empQualId);
        return ResponseEntity.noContent().build();
    }

    @Operation(summary = "Reverse lookup: who holds this qualification", description = "Requires `employee.read`.")
    @GetMapping("/qualifications/{id}/employees")
    @RequirePermission("employee.read")
    public List<QualificationEmployeeDto> employeesWithQualification(@PathVariable long id) {
        return service.employeesWithQualification(id);
    }
}
