package com.forward.hrms.bff.service;

import com.forward.hrms.bff.client.AuditClient;
import com.forward.hrms.bff.client.AuthClient;
import com.forward.hrms.auth.dto.response.MeResponse;
import com.forward.hrms.auth.dto.response.SessionDto;
import com.forward.hrms.audit.dto.response.TimelineEntry;
import com.forward.hrms.audit.dto.response.TimelinePage;
import com.forward.hrms.bff.dto.response.AccountSessionsView;
import com.forward.hrms.bff.dto.response.AccountSummary;
import com.forward.hrms.commons.core.context.CurrentUser;
import com.forward.hrms.commons.core.context.CurrentUserHolder;
import com.forward.hrms.commons.core.error.CommonErrorCode;
import com.forward.hrms.commons.core.error.UnauthorizedError;
import com.forward.hrms.commons.web.async.RequestPropagatingExecutor;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import org.springframework.stereotype.Service;

import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;

/** Composes /bff/account/summary by fanning out to auth + audit in parallel. */
@Slf4j
@Service
@RequiredArgsConstructor
public class AccountSummaryService {

    /** Default size for the recent-events list — matches the wishlist target. */
    private static final int RECENT_EVENTS_LIMIT = 20;

    private final AuthClient auth;
    private final AuditClient audit;
    private final RequestPropagatingExecutor propagating;

    public AccountSummary summary() {
        CurrentUser cu = CurrentUserHolder.get()
                .orElseThrow(() -> new UnauthorizedError(
                        CommonErrorCode.UNAUTHENTICATED, "no current user"));

        // Run the three downstream calls in parallel on virtual threads.
        // RequestPropagatingExecutor rebinds X-User-* / SecurityContext on
        // the worker thread so commons-web's IdentityForwardingInterceptor
        // sees the inbound request's identity.
        CompletableFuture<MeResponse> meF = propagating.async(auth::me);
        CompletableFuture<List<SessionDto>> sessionsF = propagating.async(auth::mySessions);
        CompletableFuture<TimelinePage> timelineF = propagating.async(
                () -> audit.userTimeline(cu.userId(), RECENT_EVENTS_LIMIT));

        try {
            CompletableFuture.allOf(meF, sessionsF, timelineF).join();
            // Producers now set actorLabel on the envelope (X-User-FullName →
            // EventEnvelope.actorLabel → audit.actor_label), so the BFF no
            // longer resolves it at read time.
            List<TimelineEntry> recent = timelineF.get() != null
                    ? timelineF.get().content()
                    : List.of();
            return new AccountSummary(
                    meF.get(),
                    partitionSessions(sessionsF.get(), cu.sessionId()),
                    recent
            );
        } catch (InterruptedException ie) {
            Thread.currentThread().interrupt();
            throw new RuntimeException("interrupted while assembling /bff/account/summary", ie);
        } catch (ExecutionException ee) {
            throw unwrap(ee);
        }
    }

    private static AccountSessionsView partitionSessions(List<SessionDto> all, long currentSessionId) {
        if (all == null || all.isEmpty()) return new AccountSessionsView(0, null, List.of());
        SessionDto current = all.stream()
                .filter(s -> s.current() || s.id() == currentSessionId)
                .findFirst()
                .orElse(null);
        List<SessionDto> others = all.stream()
                .filter(s -> current == null || s.id() != current.id())
                .toList();
        return new AccountSessionsView(all.size(), current, others);
    }

    /** §7.bff #25 — delegates to {@link com.forward.hrms.bff.service.dashboard.DashboardSupport#unwrap}. */
    private static RuntimeException unwrap(ExecutionException ee) {
        return com.forward.hrms.bff.service.dashboard.DashboardSupport.unwrap(ee);
    }
}
