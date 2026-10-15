package com.forward.hrms.employee.controller;

import com.forward.hrms.commons.web.security.RequirePermission;
import com.forward.hrms.employee.dto.request.AddPriorExperienceRequest;
import com.forward.hrms.employee.dto.request.UpdatePriorExperienceRequest;
import com.forward.hrms.employee.dto.response.PriorExperienceDto;
import com.forward.hrms.employee.service.PriorExperienceService;

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

@Tag(name = "Employees — Prior Experience",
        description = "Pre-Forward-Sports employment history. Captured by HR from the candidate's CV. Requires `employee.read` / `employee.write`.")
@RestController
@RequiredArgsConstructor
@RequestMapping("/employees/{id}/prior-experience")
public class PriorExperienceController {

    private final PriorExperienceService service;

    @Operation(summary = "List prior experience (most recent first)", description = "Requires `employee.read`.")
    @GetMapping
    @RequirePermission("employee.read")
    public List<PriorExperienceDto> list(@PathVariable long id) {
        return service.list(id);
    }

    @Operation(summary = "Add a prior-experience row", description = "Requires `employee.write`.")
    @ApiResponse(responseCode = "201", description = "Created.")
    @PostMapping
    @RequirePermission("employee.write")
    @ResponseStatus(HttpStatus.CREATED)
    public PriorExperienceDto add(@PathVariable long id,
                                  @Valid @RequestBody AddPriorExperienceRequest req) {
        return service.add(id, req);
    }

    @Operation(summary = "Patch a prior-experience row", description = "Requires `employee.write`.")
    @PatchMapping("/{expId}")
    @RequirePermission("employee.write")
    public PriorExperienceDto update(@PathVariable long id, @PathVariable long expId,
                                     @Valid @RequestBody UpdatePriorExperienceRequest req) {
        return service.update(id, expId, req);
    }

    @Operation(summary = "Delete a prior-experience row", description = "Requires `employee.write`.")
    @ApiResponse(responseCode = "204", description = "Deleted.")
    @DeleteMapping("/{expId}")
    @RequirePermission("employee.write")
    public ResponseEntity<Void> delete(@PathVariable long id, @PathVariable long expId) {
        service.delete(id, expId);
        return ResponseEntity.noContent().build();
    }
}
