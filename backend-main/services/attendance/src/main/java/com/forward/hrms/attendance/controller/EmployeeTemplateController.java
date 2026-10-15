package com.forward.hrms.attendance.controller;

import com.forward.hrms.attendance.dto.request.UploadTemplateRequest;
import com.forward.hrms.attendance.dto.response.EmployeeTemplateDto;
import com.forward.hrms.attendance.service.DeviceSyncQueueService;
import com.forward.hrms.commons.web.security.RequirePermission;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;

import jakarta.validation.Valid;

import lombok.RequiredArgsConstructor;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "Employee templates (poller)",
        description = "Service-account surface for the attendance-poller sidecar to fetch template " +
                "bytes for a PUSH_TEMPLATE task and to upload newly captured templates from the " +
                "enrollment device. Upload is store-only — provisioning to devices is a separate, " +
                "operator-chosen step (POST /devices/{id}/users/{employeeId}/push-templates). " +
                "Requires `attendance.poller`.")
@RestController
@RequestMapping("/templates")
@RequiredArgsConstructor
public class EmployeeTemplateController {

    private final DeviceSyncQueueService sync;

    @Operation(summary = "Fetch a template's bytes",
            description = "Returns the base64-encoded template payload for the poller to push to the " +
                    "target device. Referenced via `templateId` in the PUSH_TEMPLATE task payload.")
    @ApiResponse(responseCode = "404", description = "TEMPLATE_NOT_FOUND.")
    @GetMapping("/{templateId}")
    @RequirePermission("attendance.poller")
    public EmployeeTemplateDto get(@PathVariable long templateId) {
        return sync.getTemplate(templateId);
    }

    @Operation(summary = "Upload a captured template",
            description = "Stores the template (one per (employee, kind, fingerId) — re-captures " +
                    "replace). Store-only: does NOT queue any push. Provisioning to a device is a " +
                    "separate, operator-chosen step via POST /devices/{id}/users/{employeeId}/push-templates.")
    @ApiResponse(responseCode = "404", description = "DEVICE_NOT_FOUND on `capturedDeviceId`.")
    @ApiResponse(responseCode = "422",
            description = "TEMPLATE_KIND_INVALID — fingerId out of range, missing for FINGERPRINT, " +
                    "or set for FACE; or bytes are not valid base64.")
    @PostMapping
    @RequirePermission("attendance.poller")
    @ResponseStatus(HttpStatus.CREATED)
    public EmployeeTemplateDto upload(@Valid @RequestBody UploadTemplateRequest req) {
        return sync.uploadTemplate(req);
    }
}
