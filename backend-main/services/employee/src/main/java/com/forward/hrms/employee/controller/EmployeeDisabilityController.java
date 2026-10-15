package com.forward.hrms.employee.controller;

import com.forward.hrms.commons.web.security.RequirePermission;
import com.forward.hrms.employee.dto.request.AddEmployeeDisabilityRequest;
import com.forward.hrms.employee.dto.request.UpdateEmployeeDisabilityRequest;
import com.forward.hrms.employee.dto.response.EmployeeDisabilityDto;
import com.forward.hrms.employee.service.EmployeeDisabilityService;

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

@Tag(name = "Employees — Disabilities",
        description = "An employee's links to disability categories. Used for PWD ordinance compliance " +
                "reporting. Requires `employee.read` / `employee.write`.")
@RestController
@RequestMapping("/employees/{id}/disabilities")
@RequiredArgsConstructor
public class EmployeeDisabilityController {

    private final EmployeeDisabilityService service;

    @Operation(summary = "List employee disabilities", description = "Requires `employee.read`.")
    @GetMapping
    @RequirePermission("employee.read")
    public List<EmployeeDisabilityDto> list(@PathVariable long id) {
        return service.list(id);
    }

    @Operation(summary = "Link a disability to the employee", description = "Requires `employee.write`.")
    @ApiResponse(responseCode = "201", description = "Linked.")
    @ApiResponse(responseCode = "409", description = "EMPLOYEE_DISABILITY_DUPLICATE.")
    @PostMapping
    @RequirePermission("employee.write")
    @ResponseStatus(HttpStatus.CREATED)
    public EmployeeDisabilityDto add(@PathVariable long id,
                                     @Valid @RequestBody AddEmployeeDisabilityRequest req) {
        return service.add(id, req);
    }

    @Operation(summary = "Patch description / severity / document",
            description = "The disability id is immutable on this row — delete and re-add to change it. " +
                    "Requires `employee.write`.")
    @PatchMapping("/{linkId}")
    @RequirePermission("employee.write")
    public EmployeeDisabilityDto update(@PathVariable long id, @PathVariable long linkId,
                                        @Valid @RequestBody UpdateEmployeeDisabilityRequest req) {
        return service.update(id, linkId, req);
    }

    @Operation(summary = "Remove a disability link", description = "Requires `employee.write`.")
    @ApiResponse(responseCode = "204", description = "Deleted.")
    @DeleteMapping("/{linkId}")
    @RequirePermission("employee.write")
    public ResponseEntity<Void> delete(@PathVariable long id, @PathVariable long linkId) {
        service.delete(id, linkId);
        return ResponseEntity.noContent().build();
    }
}
