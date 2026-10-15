package com.forward.hrms.bff.integration;

import com.forward.hrms.audit.dto.response.TimelineEntry;
import com.forward.hrms.audit.dto.response.TimelinePage;
import com.forward.hrms.auth.dto.response.MeResponse;
import com.forward.hrms.auth.dto.response.SessionDto;
import com.forward.hrms.bff.client.AuditClient;
import com.forward.hrms.bff.client.AuthClient;
import com.forward.hrms.bff.dto.response.AccountSummary;
import com.forward.hrms.bff.service.AccountSummaryService;
import com.forward.hrms.commons.core.context.CurrentUser;
import com.forward.hrms.commons.core.context.CurrentUserHolder;
import com.forward.hrms.commons.core.error.UnauthorizedError;
import com.forward.hrms.commons.testsupport.containers.WithTestcontainers;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.anyInt;
import static org.mockito.Mockito.anyLong;
import static org.mockito.Mockito.when;

/**
 * BFF composite contract: {@code GET /bff/account/summary}. Asserts the
 * shape FE consumes — identity, sessions partitioned into current + others,
 * recent timeline. Downstream auth + audit clients are mocked so we test
 * the composition logic, not transport.
 */
/*
 * BFF has no DataSource, but commons-test pulls JPA into the test
 * classpath, which trips OutboxAutoConfiguration's @ConditionalOnClass
 * on Entity.class. Disable the outbox in tests.
 */
@SpringBootTest(properties = "hrms.outbox.enabled=false")
@WithTestcontainers
class AccountSummaryIntegrationTest {

    @Autowired AccountSummaryService service;
    @MockitoBean AuthClient authClient;
    @MockitoBean AuditClient auditClient;

    private static final long USER_ID = 7L;
    private static final long CURRENT_SESSION_ID = 100L;

    @BeforeEach
    void setIdentity() {
        CurrentUserHolder.set(new CurrentUser(USER_ID, "test-user", CURRENT_SESSION_ID,
                Set.of("USER"), Set.of()));
    }

    @AfterEach
    void clearIdentity() {
        CurrentUserHolder.clear();
    }

    @Test
    void summary_assemblesAuthAndAuditPayloads_partitionsCurrentSession() {
        MeResponse me = new MeResponse(USER_ID, "test-user", "Test User",
                "test@example.com", true, false,
                List.of("USER"), List.of("auth.me.read"), LocalDateTime.now().minusHours(1));

        SessionDto current = sessionStub(CURRENT_SESSION_ID, true);
        SessionDto other1 = sessionStub(101L, false);
        SessionDto other2 = sessionStub(102L, false);
        when(authClient.me()).thenReturn(me);
        when(authClient.mySessions()).thenReturn(List.of(current, other1, other2));

        TimelineEntry e1 = new TimelineEntry(1L, "logged_in", null, "test-user",
                "Test User", "Logged in.", LocalDateTime.now().minusHours(2));
        TimelineEntry e2 = new TimelineEntry(2L, "session_revoked", null, "test-user",
                "Test User", "Session revoked.", LocalDateTime.now().minusHours(5));
        when(auditClient.userTimeline(anyLong(), anyInt()))
                .thenReturn(new TimelinePage(List.of(e1, e2), null));

        AccountSummary out = service.summary();

        assertThat(out.identity()).isEqualTo(me);
        assertThat(out.sessions().active()).isEqualTo(3);
        assertThat(out.sessions().current()).isEqualTo(current);
        assertThat(out.sessions().others())
                .containsExactlyInAnyOrder(other1, other2);
        assertThat(out.recentEvents()).containsExactly(e1, e2);
    }

    @Test
    void summary_emptySessions_yieldZeroAndNullCurrent() {
        when(authClient.me()).thenReturn(stubMe());
        when(authClient.mySessions()).thenReturn(List.of());
        when(auditClient.userTimeline(anyLong(), anyInt()))
                .thenReturn(new TimelinePage(List.of(), null));

        AccountSummary out = service.summary();
        assertThat(out.sessions().active()).isZero();
        assertThat(out.sessions().current()).isNull();
        assertThat(out.sessions().others()).isEmpty();
    }

    @Test
    void summary_nullTimelinePage_yieldsEmptyEvents() {
        when(authClient.me()).thenReturn(stubMe());
        when(authClient.mySessions()).thenReturn(List.of(sessionStub(CURRENT_SESSION_ID, true)));
        when(auditClient.userTimeline(anyLong(), anyInt())).thenReturn(null);

        AccountSummary out = service.summary();
        assertThat(out.recentEvents()).isEmpty();
    }

    @Test
    void summary_withNoCurrentUser_throws401() {
        CurrentUserHolder.clear();
        assertThatThrownBy(() -> service.summary())
                .isInstanceOf(UnauthorizedError.class);
    }

    private MeResponse stubMe() {
        return new MeResponse(USER_ID, "test-user", "Test User",
                "test@example.com", true, false,
                List.of("USER"), List.of(), null);
    }

    private static SessionDto sessionStub(long id, boolean current) {
        return new SessionDto(
                id,
                "Chrome on macOS",
                "DESKTOP",
                new SessionDto.NameAndVersion("Chrome", "118"),
                new SessionDto.NameAndVersion("macOS", "14"),
                "203.0.113.1",
                "Mozilla/5.0",
                LocalDateTime.now().minusHours(1),
                LocalDateTime.now(),
                LocalDateTime.now().plusDays(7),
                current,
                null,
                null);
    }
}
