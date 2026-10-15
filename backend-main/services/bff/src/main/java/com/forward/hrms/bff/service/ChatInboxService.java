package com.forward.hrms.bff.service;

import com.forward.hrms.bff.client.ChatClient;
import com.forward.hrms.chat.dto.response.ConversationDto;
import com.forward.hrms.chat.dto.response.PresenceDto;
import com.forward.hrms.bff.dto.response.ChatInboxResponse;
import com.forward.hrms.bff.dto.response.UserIdentitySummary;
import com.forward.hrms.bff.dto.response.UsersByIdsResponse;
import com.forward.hrms.commons.core.context.CurrentUserHolder;
import com.forward.hrms.commons.web.async.RequestPropagatingExecutor;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.function.Supplier;

/**
 * Composes {@code /bff/chat/inbox} for the chat surface's first paint.
 * Step 1 fetches conversations (we need the member ids before we can
 * resolve identity / presence). Step 2 fans out the resolver + presence
 * in parallel against the union of member ids.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ChatInboxService {

    private final ChatClient chat;
    private final UserIdentityService userIdentityService;
    private final RequestPropagatingExecutor propagating;

    public ChatInboxResponse inbox() {
        List<ConversationDto> conversations = chat.myConversations();
        if (conversations.isEmpty()) {
            return new ChatInboxResponse(List.of(), Map.of(), List.of());
        }

        // Union of every member id across every conversation.
        Set<Long> userIds = new LinkedHashSet<>();
        for (ConversationDto c : conversations) {
            if (c.memberUserIds() != null) userIds.addAll(c.memberUserIds());
        }
        // Always include the caller — DM rows where the caller is the
        // only member of record (rare edge during onboarding) still
        // need the caller's own identity.
        CurrentUserHolder.get().ifPresent(cu -> userIds.add(cu.userId()));
        if (userIds.isEmpty()) {
            return new ChatInboxResponse(conversations, Map.of(), List.of());
        }
        List<Long> userIdList = new ArrayList<>(userIds);

        CompletableFuture<UsersByIdsResponse> resolvedF =
                supplyWithRequest(() -> userIdentityService.byIds(userIdList));
        CompletableFuture<List<PresenceDto>> presenceF =
                supplyWithRequest(() -> chat.presence(userIdList));

        try {
            CompletableFuture.allOf(resolvedF, presenceF).join();
            Map<Long, UserIdentitySummary> resolvedMap = new HashMap<>();
            for (UserIdentitySummary s : resolvedF.get().users()) resolvedMap.put(s.userId(), s);
            return new ChatInboxResponse(conversations, resolvedMap, presenceF.get());
        } catch (InterruptedException ie) {
            Thread.currentThread().interrupt();
            throw new RuntimeException("interrupted while assembling /bff/chat/inbox", ie);
        } catch (ExecutionException ee) {
            throw unwrap(ee);
        }
    }

    private <T> CompletableFuture<T> supplyWithRequest(Supplier<T> supplier) {
        return propagating.async(supplier);
    }

    /** §7.bff #25 — delegates to {@link com.forward.hrms.bff.service.dashboard.DashboardSupport#unwrap}. */
    private static RuntimeException unwrap(ExecutionException ee) {
        return com.forward.hrms.bff.service.dashboard.DashboardSupport.unwrap(ee);
    }
}
