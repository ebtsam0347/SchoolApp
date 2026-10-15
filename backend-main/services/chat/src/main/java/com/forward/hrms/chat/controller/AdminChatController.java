package com.forward.hrms.chat.controller;

import com.forward.hrms.chat.dto.response.ConversationDto;
import com.forward.hrms.chat.dto.response.MessagePageResponse;
import com.forward.hrms.chat.service.ConversationService;
import com.forward.hrms.chat.service.MessageService;
import com.forward.hrms.commons.web.security.RequirePermission;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;

import lombok.RequiredArgsConstructor;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@Tag(name = "Chat — Admin",
        description = "Admin/HR review endpoints. Caller must hold `chat.conversations.read`. Reads bypass membership; intended for compliance review of any conversation.")
@RestController
@RequestMapping("/chat/admin")
@RequiredArgsConstructor
public class AdminChatController {

    private final ConversationService conversationService;
    private final MessageService messageService;

    @Operation(summary = "List a user's conversations (admin)",
            description = "Includes the org-wide BROADCAST. Unread counts are reported from the target user's perspective.")
    @ApiResponse(responseCode = "200")
    @GetMapping("/conversations")
    @RequirePermission("chat.conversations.read")
    public List<ConversationDto> listForUser(@RequestParam("userId") long userId) {
        return conversationService.listForUser(userId);
    }

    @Operation(summary = "Get any conversation header (admin)",
            description = "Returns members + unread count from the target user's perspective. Membership is not enforced for the caller.")
    @ApiResponse(responseCode = "200")
    @GetMapping("/conversations/{id}")
    @RequirePermission("chat.conversations.read")
    public ConversationDto getForUser(@PathVariable("id") long conversationId,
                                       @RequestParam(value = "asUserId", required = false, defaultValue = "0") long asUserId) {
        return conversationService.adminGet(asUserId, conversationId);
    }

    @Operation(summary = "Page any conversation's messages (admin)",
            description = "Same shape as the regular paged messages endpoint, but skips the membership check. Useful for compliance review.")
    @ApiResponse(responseCode = "200")
    @GetMapping("/conversations/{id}/messages")
    @RequirePermission("chat.conversations.read")
    public MessagePageResponse pageMessages(@PathVariable("id") long conversationId,
                                             @RequestParam(value = "beforeId", required = false) Long beforeId,
                                             @RequestParam(value = "limit", defaultValue = "50") int limit) {
        return messageService.adminPage(conversationId, beforeId, limit);
    }
}
