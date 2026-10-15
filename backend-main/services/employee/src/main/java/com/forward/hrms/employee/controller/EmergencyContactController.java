package com.forward.hrms.employee.controller;

import com.forward.hrms.commons.web.security.RequirePermission;
import com.forward.hrms.employee.dto.request.CreateEmergencyContactRequest;
import com.forward.hrms.employee.dto.request.UpdateEmergencyContactRequest;
import com.forward.hrms.employee.dto.response.EmergencyContactDto;
import com.forward.hrms.employee.service.EmergencyContactService;

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

@Tag(name = "Employees — Emergency Contacts",
        description = "HR-facing CRUD on an employee's emergency contacts. Setting `primary=true` " +
                "demotes any other primary on the same employee. Requires `employee.read` / `employee.write`.")
@RestController
@RequestMapping("/employees/{id}/emergency-contacts")
@RequiredArgsConstructor
public class EmergencyContactController {

    private final EmergencyContactService service;

    @Operation(summary = "List emergency contacts (primary first)", description = "Requires `employee.read`.")
    @GetMapping
    @RequirePermission("employee.read")
    public List<EmergencyContactDto> list(@PathVariable long id) {
        return service.list(id);
    }

    @Operation(summary = "Add an emergency contact", description = "Requires `employee.write`.")
    @ApiResponse(responseCode = "201", description = "Created.")
    @PostMapping
    @RequirePermission("employee.write")
    @ResponseStatus(HttpStatus.CREATED)
    public EmergencyContactDto create(@PathVariable long id,
                                      @Valid @RequestBody CreateEmergencyContactRequest req) {
        return service.create(id, req);
    }

    @Operation(summary = "Patch an emergency contact", description = "Requires `employee.write`.")
    @PatchMapping("/{contactId}")
    @RequirePermission("employee.write")
    public EmergencyContactDto update(@PathVariable long id, @PathVariable long contactId,
                                      @Valid @RequestBody UpdateEmergencyContactRequest req) {
        return service.update(id, contactId, req);
    }

    @Operation(summary = "Delete an emergency contact", description = "Requires `employee.write`.")
    @ApiResponse(responseCode = "204", description = "Deleted.")
    @DeleteMapping("/{contactId}")
    @RequirePermission("employee.write")
    public ResponseEntity<Void> delete(@PathVariable long id, @PathVariable long contactId) {
        service.delete(id, contactId);
        return ResponseEntity.noContent().build();
    }
}
