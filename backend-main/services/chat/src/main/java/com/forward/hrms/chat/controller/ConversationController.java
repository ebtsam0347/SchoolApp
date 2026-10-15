package com.forward.hrms.chat.controller;

import com.forward.hrms.chat.dto.request.AddMemberRequest;
import com.forward.hrms.chat.dto.request.CreateDmRequest;
import com.forward.hrms.chat.dto.request.CreateGroupRequest;
import com.forward.hrms.chat.dto.request.MarkReadRequest;
import com.forward.hrms.chat.dto.response.ConversationDto;
import com.forward.hrms.chat.entity.Conversation;
import com.forward.hrms.chat.service.ConversationService;
import com.forward.hrms.commons.core.context.CurrentUserHolder;
import com.forward.hrms.commons.core.error.CommonErrorCode;
import com.forward.hrms.commons.core.error.UnauthorizedError;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;

import jakarta.validation.Valid;

import lombok.RequiredArgsConstructor;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@Tag(name = "Chat — Conversations",
        description = "DMs, groups, and the org-wide announcements channel. DM creation is idempotent on the (caller, otherUser) pair.")
@RestController
@RequestMapping("/chat/conversations")
@RequiredArgsConstructor
public class ConversationController {

    private final ConversationService service;

    @Operation(summary = "List my conversations",
            description = "Conversations where I'm a member, ordered by last-message time desc. Includes unread counts and member ids for chip rendering.")
    @GetMapping
    public List<ConversationDto> mine() {
        return service.listForUser(callerUserId());
    }

    @Operation(summary = "Conversation header + members",
            description = "Returns membership and unread count. Caller must be a member, except for the BROADCAST channel which everyone can fetch.")
    @ApiResponse(responseCode = "200")
    @ApiResponse(responseCode = "403", description = "Not a member.")
    @ApiResponse(responseCode = "404", description = "Conversation not found.")
    @GetMapping("/{id}")
    public ConversationDto get(@PathVariable long id) {
        return service.get(callerUserId(), id);
    }

    @Operation(summary = "Get-or-create a 1:1 DM",
            description = "Idempotent. Returns the existing DM if one already exists between caller and `otherUserId`.")
    @ApiResponse(responseCode = "200", description = "Existing DM returned.")
    @ApiResponse(responseCode = "201", description = "New DM created.")
    @PostMapping("/dm")
    public ConversationDto createOrGetDm(@Valid @RequestBody CreateDmRequest req) {
        long me = callerUserId();
        Conversation c = service.getOrCreateDm(me, req.otherUserId());
        return service.get(me, c.getId());
    }

    @Operation(summary = "Create a group conversation",
            description = "Caller becomes OWNER. Member cap configured via `hrms.chat.group-member-cap` (default 50, including caller).")
    @ApiResponse(responseCode = "201", description = "Group created.")
    @PostMapping("/group")
    @ResponseStatus(HttpStatus.CREATED)
    public ConversationDto createGroup(@Valid @RequestBody CreateGroupRequest req) {
        long me = callerUserId();
        Conversation c = service.createGroup(me, req.title(), req.memberUserIds());
        return service.get(me, c.getId());
    }

    @Operation(summary = "Add a member to a group",
            description = "Owner-only. 409 if user already a member or member cap reached.")
    @ApiResponse(responseCode = "204")
    @PostMapping("/{id}/members")
    public ResponseEntity<Void> addMember(@PathVariable long id, @Valid @RequestBody AddMemberRequest req) {
        service.addMember(callerUserId(), id, req.userId());
        return ResponseEntity.noContent().build();
    }

    @Operation(summary = "Remove a member or leave a group",
            description = "Owners can remove anyone; members can remove themselves only. Last owner can't leave a non-empty group.")
    @ApiResponse(responseCode = "204")
    @DeleteMapping("/{id}/members/{userId}")
    public ResponseEntity<Void> removeMember(@PathVariable long id, @PathVariable long userId) {
        service.removeMember(callerUserId(), id, userId);
        return ResponseEntity.noContent().build();
    }

    @Operation(summary = "Update my read cursor",
            description = "Sets `last_read_message_id` for caller's row in this conversation. Only moves forward — older values are ignored.")
    @ApiResponse(responseCode = "204")
    @PostMapping("/{id}/read")
    public ResponseEntity<Void> markRead(@PathVariable long id, @Valid @RequestBody MarkReadRequest req) {
        service.markRead(callerUserId(), id, req.lastReadMessageId());
        return ResponseEntity.noContent().build();
    }

    private static long callerUserId() {
        return CurrentUserHolder.get()
                .map(u -> u.userId())
                .orElseThrow(() -> new UnauthorizedError(CommonErrorCode.UNAUTHENTICATED, "no current user"));
    }
}
