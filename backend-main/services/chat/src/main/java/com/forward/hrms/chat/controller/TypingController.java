package com.forward.hrms.chat.controller;

import com.forward.hrms.chat.dto.request.TypingRequest;
import com.forward.hrms.chat.service.TypingService;
import com.forward.hrms.commons.core.context.CurrentUserHolder;
import com.forward.hrms.commons.core.error.CommonErrorCode;
import com.forward.hrms.commons.core.error.UnauthorizedError;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;

import lombok.RequiredArgsConstructor;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "Chat — Typing",
        description = "Fire-and-forget typing indicator. The client debounces sending (e.g. once per 3 s while typing); the server publishes one Redis event per recipient.")
@RestController
@RequiredArgsConstructor
public class TypingController {

    private final TypingService service;

    @Operation(summary = "Signal that I'm typing in this conversation",
            description = "Server publishes `chat.typing` to each conversation member except the caller. BROADCAST conversations are skipped — typing makes no sense for one-way channels.")
    @ApiResponse(responseCode = "204")
    @PostMapping("/chat/conversations/{id}/typing")
    public ResponseEntity<Void> typing(@PathVariable("id") long conversationId,
                                        @RequestBody(required = false) TypingRequest req) {
        boolean stopped = req != null && req.isStopped();
        service.typing(callerUserId(), conversationId, stopped);
        return ResponseEntity.noContent().build();
    }

    private static long callerUserId() {
        return CurrentUserHolder.get()
                .map(u -> u.userId())
                .orElseThrow(() -> new UnauthorizedError(CommonErrorCode.UNAUTHENTICATED, "no current user"));
    }
}
