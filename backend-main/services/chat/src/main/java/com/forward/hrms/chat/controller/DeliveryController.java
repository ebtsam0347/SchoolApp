package com.forward.hrms.chat.controller;

import com.forward.hrms.chat.service.DeliveryService;
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
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "Chat — Delivery",
        description = "Per-message delivery acknowledgements. The BFF calls this after a successful SSE forward of a `chat.message` event so the sender's UI can render ✓✓ (delivered).")
@RestController
@RequiredArgsConstructor
public class DeliveryController {

    private final DeliveryService service;

    @Operation(summary = "Acknowledge delivery of a message to the caller",
            description = "Idempotent. Inserts (messageId, callerUserId) into `message_deliveries` and publishes `chat.message.delivered` to the sender's Redis channel.")
    @ApiResponse(responseCode = "204")
    @PostMapping("/chat/messages/{id}/delivery")
    public ResponseEntity<Void> delivered(@PathVariable("id") long messageId) {
        service.markDelivered(callerUserId(), messageId);
        return ResponseEntity.noContent().build();
    }

    private static long callerUserId() {
        return CurrentUserHolder.get()
                .map(u -> u.userId())
                .orElseThrow(() -> new UnauthorizedError(CommonErrorCode.UNAUTHENTICATED, "no current user"));
    }
}
