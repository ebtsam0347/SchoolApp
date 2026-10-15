package com.forward.hrms.notification.controller;

import com.forward.hrms.commons.web.security.RequirePermission;
import com.forward.hrms.notification.dto.request.AnnounceRequest;
import com.forward.hrms.notification.dto.response.AnnounceResponse;
import com.forward.hrms.notification.service.AnnounceService;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;

import jakarta.validation.Valid;

import lombok.RequiredArgsConstructor;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "Admin — Announcements",
        description = "Broadcast a notification to a resolved user-id list. Requires `notification.announce`.")
@RestController
@RequestMapping("/admin/notifications")
@RequiredArgsConstructor
public class AdminAnnounceController {

    private final AnnounceService service;

    @Operation(summary = "Send a broadcast",
            description = "Caller resolves the recipient list (employee-service department/role pickers etc.) "
                    + "and passes the user ids in. The service fans out N rows + N publishes — recipient resolution "
                    + "is not done here. Requires `notification.announce`.")
    @ApiResponse(responseCode = "201", description = "Broadcast queued; rows persisted.")
    @ApiResponse(responseCode = "422", description = "No recipients, or list exceeds the configured cap.")
    @PostMapping("/announce")
    @RequirePermission("notification.announce")
    @ResponseStatus(HttpStatus.CREATED)
    public AnnounceResponse announce(@Valid @RequestBody AnnounceRequest req) {
        return service.announce(req);
    }
}
