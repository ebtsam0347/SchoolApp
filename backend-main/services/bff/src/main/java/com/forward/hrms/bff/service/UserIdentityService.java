package com.forward.hrms.bff.service;

import com.forward.hrms.bff.client.AuthClient;
import com.forward.hrms.bff.client.EmployeeClient;
import com.forward.hrms.employee.dto.response.EmployeeIdentitySummary;
import com.forward.hrms.bff.dto.response.UserIdentitySummary;
import com.forward.hrms.bff.dto.response.UsersByIdsResponse;
import com.forward.hrms.commons.web.async.RequestPropagatingExecutor;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.function.Supplier;

/**
 * Composes {@code /bff/users/by-ids} by fanning out to auth (username +
 * fullName + status) and employee (avatar URL) in parallel. Falls back
 * gracefully when an employee row is missing — admin-only users still
 * resolve, just without an avatar.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class UserIdentityService {

    public static final int BY_IDS_MAX = 200;

    /** Page-size cap for the chat people-picker search. */
    public static final int SEARCH_MAX_SIZE = 50;

    private final AuthClient auth;
    private final EmployeeClient employees;
    private final RequestPropagatingExecutor propagating;

    /**
     * Identity-scoped people-search for the chat picker. Auth picks the
     * matching ACTIVE users; we then fan out to employee for avatars.
     * Order from auth (alphabetic by fullName) is preserved.
     */
    public UsersByIdsResponse search(String q, int size) {
        if (q == null || q.isBlank()) return new UsersByIdsResponse(List.of());
        int safeSize = Math.min(Math.max(size, 1), SEARCH_MAX_SIZE);
        List<com.forward.hrms.auth.dto.response.UserIdentitySummary> matches = auth.userIdentitiesSearch(q, safeSize);
        if (matches.isEmpty()) return new UsersByIdsResponse(List.of());

        List<Long> userIds = new ArrayList<>(matches.size());
        for (com.forward.hrms.auth.dto.response.UserIdentitySummary u : matches) userIds.add(u.id());

        // Avatars only — no need to re-fetch auth identities.
        List<EmployeeIdentitySummary> emps = employees.identitiesByUserIds(userIds);
        Map<Long, EmployeeIdentitySummary> empByUser = new HashMap<>();
        for (EmployeeIdentitySummary e : emps) empByUser.put(e.userId(), e);

        List<UserIdentitySummary> rows = new ArrayList<>(matches.size());
        for (com.forward.hrms.auth.dto.response.UserIdentitySummary u : matches) {
            EmployeeIdentitySummary e = empByUser.get(u.id());
            String fullName = (e != null && e.fullName() != null) ? e.fullName() : u.fullName();
            String avatar = e != null ? e.profilePictureUrl() : null;
            rows.add(new UserIdentitySummary(u.id(), u.username(), fullName, avatar, u.status().name()));
        }
        return new UsersByIdsResponse(rows);
    }

    public UsersByIdsResponse byIds(List<Long> ids) {
        if (ids == null || ids.isEmpty()) return new UsersByIdsResponse(List.of());

        // Preserve input order while deduping.
        Set<Long> distinct = new LinkedHashSet<>(ids);
        List<Long> deduped = new ArrayList<>(distinct);

        CompletableFuture<List<com.forward.hrms.auth.dto.response.UserIdentitySummary>> usersF =
                supplyWithRequest(() -> auth.userIdentitiesByIds(deduped));
        CompletableFuture<List<EmployeeIdentitySummary>> empsF =
                supplyWithRequest(() -> employees.identitiesByUserIds(deduped));

        try {
            CompletableFuture.allOf(usersF, empsF).join();
            Map<Long, EmployeeIdentitySummary> empByUser = new HashMap<>();
            for (EmployeeIdentitySummary e : empsF.get()) empByUser.put(e.userId(), e);

            Map<Long, com.forward.hrms.auth.dto.response.UserIdentitySummary> userById = new HashMap<>();
            for (com.forward.hrms.auth.dto.response.UserIdentitySummary u : usersF.get()) userById.put(u.id(), u);

            List<UserIdentitySummary> rows = new ArrayList<>(deduped.size());
            for (Long id : deduped) {
                com.forward.hrms.auth.dto.response.UserIdentitySummary u = userById.get(id);
                if (u == null) continue; // unknown user id — drop silently
                EmployeeIdentitySummary e = empByUser.get(id);
                String fullName = (e != null && e.fullName() != null) ? e.fullName() : u.fullName();
                String avatar = e != null ? e.profilePictureUrl() : null;
                rows.add(new UserIdentitySummary(u.id(), u.username(), fullName, avatar, u.status().name()));
            }
            return new UsersByIdsResponse(rows);
        } catch (InterruptedException ie) {
            Thread.currentThread().interrupt();
            throw new RuntimeException("interrupted while assembling /bff/users/by-ids", ie);
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
