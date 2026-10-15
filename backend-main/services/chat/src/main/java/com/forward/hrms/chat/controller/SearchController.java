package com.forward.hrms.chat.controller;

import com.forward.hrms.chat.dto.response.SearchResultsResponse;
import com.forward.hrms.chat.service.SearchService;
import com.forward.hrms.commons.core.context.CurrentUserHolder;
import com.forward.hrms.commons.core.error.CommonErrorCode;
import com.forward.hrms.commons.core.error.UnauthorizedError;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;

import lombok.RequiredArgsConstructor;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "Chat — Search",
        description = "Full-text message search inside a single conversation. Member-scoped — caller must be a member of the conversation (BROADCAST is open to everyone).")
@RestController
@RequestMapping("/chat/search")
@RequiredArgsConstructor
public class SearchController {

    private final SearchService service;

    @Operation(summary = "Search messages in a conversation",
            description = "Postgres tsvector + GIN under the hood. Multi-word queries are AND-combined; soft-deleted messages are excluded. " +
                    "Response embeds the conversation header so the FE can render thread title without a separate fetch.")
    @ApiResponse(responseCode = "200")
    @ApiResponse(responseCode = "403", description = "Caller is not a member of the conversation.")
    @GetMapping
    public SearchResultsResponse search(
            @RequestParam("q") String q,
            @RequestParam("conversationId") long conversationId,
            @RequestParam(value = "limit", defaultValue = "50") int limit) {
        return service.search(callerUserId(), conversationId, q, limit);
    }

    private static long callerUserId() {
        return CurrentUserHolder.get()
                .map(u -> u.userId())
                .orElseThrow(() -> new UnauthorizedError(CommonErrorCode.UNAUTHENTICATED, "no current user"));
    }
}
