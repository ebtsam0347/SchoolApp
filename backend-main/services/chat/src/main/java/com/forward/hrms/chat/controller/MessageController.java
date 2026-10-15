package com.forward.hrms.chat.controller;

import com.forward.hrms.chat.dto.request.EditMessageRequest;
import com.forward.hrms.chat.dto.request.SendMessageRequest;
import com.forward.hrms.chat.dto.response.MessageDto;
import com.forward.hrms.chat.dto.response.MessagePageResponse;
import com.forward.hrms.chat.service.MessageService;
import com.forward.hrms.commons.core.context.CurrentUserHolder;
import com.forward.hrms.commons.core.error.CommonErrorCode;
import com.forward.hrms.commons.core.error.UnauthorizedError;
import com.forward.hrms.commons.web.security.RequirePermission;

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
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "Chat — Messages",
        description = "Send, edit (within 15 min), soft-delete, and page through messages. Realtime delivery is on the BFF SSE stream as `chat.message` events.")
@RestController
@RequiredArgsConstructor
public class MessageController {

    private final MessageService service;

    @Operation(summary = "Page messages in a conversation (newest first)",
            description = "Keyset pagination by message id desc. Pass the lowest id from the previous page as `beforeId` for the next older page.")
    @GetMapping("/chat/conversations/{id}/messages")
    public MessagePageResponse page(
            @PathVariable("id") long conversationId,
            @RequestParam(value = "beforeId", required = false) Long beforeId,
            @RequestParam(value = "limit", defaultValue = "50") int limit) {
        return service.page(callerUserId(), conversationId, beforeId, limit);
    }

    @Operation(summary = "Send a message",
            description = "Caller must be a member of the conversation. Returns the persisted message; live delivery to other members is dispatched after the transaction commits.")
    @ApiResponse(responseCode = "201")
    @PostMapping("/chat/conversations/{id}/messages")
    @ResponseStatus(HttpStatus.CREATED)
    public MessageDto send(@PathVariable("id") long conversationId, @Valid @RequestBody SendMessageRequest req) {
        return service.send(callerUserId(), conversationId, req);
    }

    @Operation(summary = "Edit your own message",
            description = "Allowed within the edit window (default 15 min). 409 outside the window or on a deleted message.")
    @ApiResponse(responseCode = "200")
    @ApiResponse(responseCode = "403", description = "Not the sender.")
    @ApiResponse(responseCode = "409", description = "Edit window expired or message deleted.")
    @PatchMapping("/chat/messages/{id}")
    public MessageDto edit(@PathVariable("id") long messageId, @Valid @RequestBody EditMessageRequest req) {
        return service.edit(callerUserId(), messageId, req);
    }

    @Operation(summary = "Soft-delete your own message")
    @ApiResponse(responseCode = "204")
    @DeleteMapping("/chat/messages/{id}")
    public ResponseEntity<Void> deleteOwn(@PathVariable("id") long messageId) {
        service.deleteOwn(callerUserId(), messageId);
        return ResponseEntity.noContent().build();
    }

    @Operation(summary = "Admin hard-delete (audit-emitting)",
            description = "Soft-deletes any message; emits an audit event. Requires `chat.messages.delete`.")
    @ApiResponse(responseCode = "204")
    @DeleteMapping("/chat/messages/{id}/admin")
    @RequirePermission("chat.messages.delete")
    public ResponseEntity<Void> deleteAsAdmin(@PathVariable("id") long messageId) {
        service.deleteAsAdmin(callerUserId(), messageId);
        return ResponseEntity.noContent().build();
    }

    private static long callerUserId() {
        return CurrentUserHolder.get()
                .map(u -> u.userId())
                .orElseThrow(() -> new UnauthorizedError(CommonErrorCode.UNAUTHENTICATED, "no current user"));
    }
}
