package com.forward.hrms.bff.service;

import com.forward.hrms.auth.dto.response.LoginHistoryPage;
import com.forward.hrms.auth.dto.response.SessionDto;
import com.forward.hrms.auth.dto.response.UserDto;
import com.forward.hrms.bff.client.AuditClient;
import com.forward.hrms.bff.client.AuthClient;
import com.forward.hrms.audit.dto.response.AuditEventDto;
import com.forward.hrms.bff.client.dto.PageView;
import com.forward.hrms.auth.dto.response.RoleDto;
import com.forward.hrms.bff.dto.response.UserOverview;
import com.forward.hrms.commons.web.async.RequestPropagatingExecutor;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import org.springframework.stereotype.Service;

import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;

/** Composes /bff/admin/users/{id}/overview by fanning out to auth + audit in parallel. */
@Slf4j
@Service
@RequiredArgsConstructor
public class AdminUserOverviewService {

    private static final int LOGIN_HISTORY_LIMIT = 50;
    private static final int RECENT_ACTIONS_LIMIT = 25;

    private final AuthClient auth;
    private final AuditClient audit;
    private final RequestPropagatingExecutor propagating;

    public UserOverview overview(long userId) {
        // The five downstream calls. Each downstream endpoint runs its
        // own @RequirePermission gate (auth.users.read, auth.sessions.admin,
        // auth.roles.read, audit.read). If the caller is missing any of
        // them, the matching downstream call fails — we don't try to
        // hide that here.
        CompletableFuture<UserDto> userF = supplyWithRequest(() -> auth.adminUser(userId));
        CompletableFuture<List<RoleDto>> rolesF = supplyWithRequest(auth::adminRoles);
        CompletableFuture<List<SessionDto>> sessionsF = supplyWithRequest(() -> auth.adminUserSessions(userId));
        CompletableFuture<LoginHistoryPage> historyF = supplyWithRequest(
                () -> auth.adminLoginHistory(userId, LOGIN_HISTORY_LIMIT));
        CompletableFuture<PageView<AuditEventDto>> recentF = supplyWithRequest(
                () -> audit.adminByActor(userId, RECENT_ACTIONS_LIMIT));

        try {
            CompletableFuture.allOf(userF, rolesF, sessionsF, historyF, recentF).join();
            return new UserOverview(
                    userF.get(),
                    rolesF.get(),
                    sessionsF.get(),
                    AuthClient.contentOrEmpty(historyF.get()),
                    recentF.get() != null ? recentF.get().contentOrEmpty() : List.of()
            );
        } catch (InterruptedException ie) {
            Thread.currentThread().interrupt();
            throw new RuntimeException("interrupted while assembling user overview for " + userId, ie);
        } catch (ExecutionException ee) {
            throw unwrap(ee);
        }
    }

    private <T> CompletableFuture<T> supplyWithRequest(java.util.function.Supplier<T> supplier) {
        return propagating.async(supplier);
    }

    /** §7.bff #25 — delegates to {@link com.forward.hrms.bff.service.dashboard.DashboardSupport#unwrap}. */
    private static RuntimeException unwrap(ExecutionException ee) {
        return com.forward.hrms.bff.service.dashboard.DashboardSupport.unwrap(ee);
    }
}
