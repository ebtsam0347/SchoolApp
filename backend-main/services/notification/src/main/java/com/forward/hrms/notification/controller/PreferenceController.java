package com.forward.hrms.notification.controller;

import com.forward.hrms.notification.dto.request.UpdatePreferencesRequest;
import com.forward.hrms.notification.dto.response.PreferenceDto;
import com.forward.hrms.notification.service.NotificationService;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;

import jakarta.validation.Valid;

import lombok.RequiredArgsConstructor;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@Tag(name = "Notifications — Preferences", description = "Per-user opt-out toggles per notification type.")
@RestController
@RequestMapping("/notifications/preferences")
@RequiredArgsConstructor
public class PreferenceController {

    private final NotificationService service;

    @Operation(summary = "List my preferences",
            description = "Returns only the explicit overrides. Anything not listed defaults to enabled.")
    @GetMapping
    public List<PreferenceDto> list() {
        return service.myPreferences().stream().map(PreferenceDto::of).toList();
    }

    @Operation(summary = "Patch my preferences",
            description = "Sparse — only the keys you pass are touched.")
    @PatchMapping
    public List<PreferenceDto> update(@Valid @RequestBody UpdatePreferencesRequest req) {
        return service.updatePreferences(req.preferences()).stream().map(PreferenceDto::of).toList();
    }
}
