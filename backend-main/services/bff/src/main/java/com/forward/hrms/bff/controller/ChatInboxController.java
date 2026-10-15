package com.forward.hrms.bff.controller;

import com.forward.hrms.bff.dto.response.ChatInboxResponse;
import com.forward.hrms.bff.service.ChatInboxService;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;

import lombok.RequiredArgsConstructor;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "BFF — Chat",
        description = "Composite endpoints for the chat surface. Cuts cold-load round-trips for the FE.")
@RestController
@RequestMapping("/bff/chat")
@RequiredArgsConstructor
public class ChatInboxController {

    private final ChatInboxService service;

    @Operation(summary = "First-paint composite for the chat inbox",
            description = "Returns the caller's conversations, an identity resolver map for every member " +
                    "(userId → username + fullName + avatar), and a presence snapshot — all in one call. " +
                    "Authenticated only.")
    @ApiResponse(responseCode = "200")
    @GetMapping("/inbox")
    public ChatInboxResponse inbox() {
        return service.inbox();
    }
}
