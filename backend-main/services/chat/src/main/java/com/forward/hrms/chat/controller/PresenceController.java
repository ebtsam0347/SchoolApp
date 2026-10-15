package com.forward.hrms.chat.controller;

import com.forward.hrms.chat.dto.response.PresenceDto;
import com.forward.hrms.chat.service.PresenceService;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;

import lombok.RequiredArgsConstructor;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@Tag(name = "Chat — Presence",
        description = "Read-only presence snapshot. Status is `online` if the user has an SSE connection to the BFF in the last ~30 s, otherwise `offline` with `lastSeenAt` from the most recent SSE heartbeat.")
@RestController
@RequestMapping("/chat/presence")
@RequiredArgsConstructor
public class PresenceController {

    private final PresenceService service;

    @Operation(summary = "Get presence for a list of users",
            description = "Bulk lookup. Caller passes a comma-separated `userIds` query param; server returns one row per id the caller is permitted to see (i.e. the caller themselves plus users they share a conversation with), preserving request order. IDs the caller doesn't share a conversation with are silently omitted to prevent presence enumeration of arbitrary user ids.")
    @ApiResponse(responseCode = "200")
    @GetMapping
    public List<PresenceDto> get(@RequestParam("userIds") List<Long> userIds) {
        return service.snapshot(userIds);
    }
}
