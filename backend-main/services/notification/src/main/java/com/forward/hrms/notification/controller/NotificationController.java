package com.forward.hrms.notification.controller;

import com.forward.hrms.commons.web.page.PageResponse;
import com.forward.hrms.notification.dto.request.BulkMarkReadRequest;
import com.forward.hrms.notification.dto.response.MarkReadResponse;
import com.forward.hrms.notification.dto.response.NotificationDto;
import com.forward.hrms.notification.dto.response.NotificationTypeDto;
import com.forward.hrms.notification.dto.response.UnreadCountResponse;
import com.forward.hrms.notification.enums.NotificationCategory;
import com.forward.hrms.notification.service.NotificationService;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;

import jakarta.validation.Valid;

import lombok.RequiredArgsConstructor;

import org.springframework.data.domain.Pageable;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@Tag(name = "Notifications — Bell feed",
        description = "Per-user in-app notifications. Authenticated; reads/writes are always scoped to the caller.")
@RestController
@RequestMapping("/notifications")
@RequiredArgsConstructor
public class NotificationController {

    private final NotificationService service;

    @Operation(summary = "List my notifications (newest first)",
            description = "Standard `page`, `size`, `sort` query params apply (default sort: `createdAt,desc`). "
                    + "Filter by `category` to restrict to a single bell-UI grouping.")
    @GetMapping
    public PageResponse<NotificationDto> list(
            @Parameter(description = "Set true to return only unread items.")
            @RequestParam(defaultValue = "false") boolean unread,
            @Parameter(description = "Filter by category. Omit for all.")
            @RequestParam(required = false) NotificationCategory category,
            @Parameter(hidden = true) Pageable pageable) {
        return service.list(unread, category, pageable);
    }

    @Operation(summary = "Bell-badge count",
            description = "Cheap unread count (Redis-cached). Falls back to a DB scan on cache miss.")
    @GetMapping("/unread-count")
    public UnreadCountResponse unreadCount() {
        return service.unreadCount();
    }

    @Operation(summary = "Type catalog",
            description = "All active notification types — what the FE preferences page renders as toggle rows, "
                    + "and what the admin templates list shows. Backed by the active rows in "
                    + "notification_templates so adding a new type means adding a template, not a code change.")
    @GetMapping("/types")
    public List<NotificationTypeDto> types() {
        return service.listTypes();
    }

    @Operation(summary = "Mark a notification read")
    @ApiResponse(responseCode = "200", description = "Marked read.")
    @ApiResponse(responseCode = "404", description = "Not found or not yours.")
    @PostMapping("/{id}/read")
    public NotificationDto markRead(@PathVariable long id) {
        return service.markRead(id);
    }

    @Operation(summary = "Bulk mark-read",
            description = "Mark a set of ids read in one call. Ids that don't belong to the caller or that are "
                    + "already read are silently skipped — `marked` reflects only rows that actually transitioned.")
    @PostMapping("/read")
    public MarkReadResponse markRead(@Valid @RequestBody BulkMarkReadRequest req) {
        return new MarkReadResponse(service.markRead(req.ids()));
    }

    @Operation(summary = "Mark all my notifications read")
    @PostMapping("/read-all")
    public UnreadCountResponse markAllRead() {
        service.markAllRead();
        return new UnreadCountResponse(0);
    }
}
