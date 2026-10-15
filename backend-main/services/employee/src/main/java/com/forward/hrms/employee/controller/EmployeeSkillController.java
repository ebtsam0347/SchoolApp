package com.forward.hrms.employee.controller;

import com.forward.hrms.commons.web.security.RequirePermission;
import com.forward.hrms.employee.dto.request.AddEmployeeSkillRequest;
import com.forward.hrms.employee.dto.request.UpdateEmployeeSkillRequest;
import com.forward.hrms.employee.dto.response.EmployeeSkillDto;
import com.forward.hrms.employee.dto.response.SkillEmployeeDto;
import com.forward.hrms.employee.service.EmployeeSkillService;

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

@Tag(name = "Employees — Skills",
        description = "Per-employee proficiency on catalog skills, plus the reverse-lookup endpoint. Requires `employee.read` / `employee.write`.")
@RestController
@RequiredArgsConstructor
@RequestMapping
public class EmployeeSkillController {

    private final EmployeeSkillService service;

    @Operation(summary = "List an employee's skills", description = "Requires `employee.read`.")
    @GetMapping("/employees/{id}/skills")
    @RequirePermission("employee.read")
    public List<EmployeeSkillDto> list(@PathVariable long id) {
        return service.list(id);
    }

    @Operation(summary = "Attach a catalog skill to an employee", description = "Requires `employee.write`.")
    @ApiResponse(responseCode = "201", description = "Created.")
    @ApiResponse(responseCode = "409", description = "Skill already attached, or skill is inactive.")
    @PostMapping("/employees/{id}/skills")
    @RequirePermission("employee.write")
    @ResponseStatus(HttpStatus.CREATED)
    public EmployeeSkillDto add(@PathVariable long id, @Valid @RequestBody AddEmployeeSkillRequest req) {
        return service.add(id, req);
    }

    @Operation(summary = "Patch an employee skill row", description = "Requires `employee.write`.")
    @PatchMapping("/employees/{id}/skills/{employeeSkillId}")
    @RequirePermission("employee.write")
    public EmployeeSkillDto update(@PathVariable long id, @PathVariable long employeeSkillId,
                                   @Valid @RequestBody UpdateEmployeeSkillRequest req) {
        return service.update(id, employeeSkillId, req);
    }

    @Operation(summary = "Detach a skill from an employee", description = "Requires `employee.write`.")
    @ApiResponse(responseCode = "204", description = "Removed.")
    @DeleteMapping("/employees/{id}/skills/{employeeSkillId}")
    @RequirePermission("employee.write")
    public ResponseEntity<Void> delete(@PathVariable long id, @PathVariable long employeeSkillId) {
        service.delete(id, employeeSkillId);
        return ResponseEntity.noContent().build();
    }

    @Operation(summary = "Reverse lookup: who holds this skill",
            description = "All employees with the given catalog skill, with their proficiency. Requires `employee.read`.")
    @GetMapping("/skills/{id}/employees")
    @RequirePermission("employee.read")
    public List<SkillEmployeeDto> employeesWithSkill(@PathVariable long id) {
        return service.employeesWithSkill(id);
    }
}
