package com.forward.hrms.chat.controller;

import com.forward.hrms.chat.dto.request.SendMessageRequest;
import com.forward.hrms.chat.dto.response.MessageDto;
import com.forward.hrms.chat.entity.Conversation;
import com.forward.hrms.chat.enums.ChatErrorCode;
import com.forward.hrms.chat.enums.ConversationType;
import com.forward.hrms.chat.repository.ConversationRepository;
import com.forward.hrms.chat.service.MessageService;
import com.forward.hrms.commons.core.context.CurrentUserHolder;
import com.forward.hrms.commons.core.error.CommonErrorCode;
import com.forward.hrms.commons.core.error.NotFoundError;
import com.forward.hrms.commons.core.error.UnauthorizedError;
import com.forward.hrms.commons.web.security.RequirePermission;

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

@Tag(name = "Chat — Broadcast",
        description = "Org-wide announcement channel. Posting requires `chat.broadcast.write`; everyone can read. Live SSE delivery via the global `chat:broadcast` channel.")
@RestController
@RequestMapping("/chat/broadcast")
@RequiredArgsConstructor
public class BroadcastController {

    private final MessageService service;
    private final ConversationRepository conversations;

    @Operation(summary = "Post an announcement to the org-wide broadcast channel",
            description = "Caller must hold `chat.broadcast.write`. Live SSE fanout reaches every connected user via the global `chat:broadcast` channel; bell entries are dropped only for users in `mentionedUserIds`.")
    @ApiResponse(responseCode = "201")
    @PostMapping("/messages")
    @RequirePermission("chat.broadcast.write")
    @ResponseStatus(HttpStatus.CREATED)
    public MessageDto post(@Valid @RequestBody SendMessageRequest req) {
        Conversation broadcast = conversations.findFirstByType(ConversationType.BROADCAST)
                .orElseThrow(() -> new NotFoundError(ChatErrorCode.BROADCAST_NOT_INITIALIZED,
                        "broadcast conversation not seeded"));
        return service.sendBroadcast(callerUserId(), broadcast.getId(), req);
    }

    private static long callerUserId() {
        return CurrentUserHolder.get()
                .map(u -> u.userId())
                .orElseThrow(() -> new UnauthorizedError(CommonErrorCode.UNAUTHENTICATED, "no current user"));
    }
}
