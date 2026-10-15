package com.forward.hrms.notification.controller;

import com.forward.hrms.commons.web.security.RequirePermission;
import com.forward.hrms.notification.dto.request.CreateTemplateRequest;
import com.forward.hrms.notification.dto.request.UpdateTemplateRequest;
import com.forward.hrms.notification.dto.response.TemplateDto;
import com.forward.hrms.notification.service.TemplateService;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
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

@Tag(name = "Admin — Templates",
        description = "Per-type rendering templates. Requires `notification.template.write` for mutations, "
                + "`notification.template.read` for reads.")
@RestController
@RequestMapping("/admin/notifications/templates")
@RequiredArgsConstructor
public class AdminTemplateController {

    private final TemplateService service;

    @Operation(summary = "List templates", description = "Requires `notification.template.read`.")
    @GetMapping
    @RequirePermission("notification.template.read")
    public List<TemplateDto> list() {
        return service.list();
    }

    @Operation(summary = "Create a template", description = "Requires `notification.template.write`.")
    @ApiResponse(responseCode = "201", description = "Created.")
    @ApiResponse(responseCode = "409", description = "Type already in use.")
    @PostMapping
    @RequirePermission("notification.template.write")
    @ResponseStatus(HttpStatus.CREATED)
    public TemplateDto create(@Valid @RequestBody CreateTemplateRequest req) {
        return service.create(req);
    }

    @Operation(summary = "Patch a template", description = "Requires `notification.template.write`.")
    @ApiResponse(responseCode = "404", description = "Template not found.")
    @PatchMapping("/{id}")
    @RequirePermission("notification.template.write")
    public TemplateDto update(@PathVariable long id, @Valid @RequestBody UpdateTemplateRequest req) {
        return service.update(id, req);
    }
}
