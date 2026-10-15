package com.forward.hrms.bff.client;

import com.forward.hrms.auth.dto.response.LoginHistoryEntry;
import com.forward.hrms.auth.dto.response.LoginHistoryPage;
import com.forward.hrms.auth.dto.response.MeResponse;
import com.forward.hrms.auth.dto.response.SessionDto;
import com.forward.hrms.auth.dto.response.UserDto;
import com.forward.hrms.auth.dto.response.UserIdentitySummary;
import com.forward.hrms.auth.dto.response.RoleDto;
import com.forward.hrms.bff.config.DownstreamProperties;
import com.forward.hrms.commons.core.error.CommonErrorCode;
import com.forward.hrms.commons.core.error.UpstreamError;

import lombok.extern.slf4j.Slf4j;

import org.springframework.core.ParameterizedTypeReference;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import java.util.List;

/**
 * Auth-service slice the BFF needs. One thin method per downstream call;
 * caller-side composition (parallel fan-out, partial degradation) lives
 * in the service layer.
 *
 * <p>Response types come from {@code auth-api} — no drift-prone
 * {@code *View} duplicates under {@code bff/client/dto/}.
 */
@Slf4j
@Component
public class AuthClient {

    private final RestClient http;

    public AuthClient(DownstreamProperties props, RestClient.Builder builder) {
        this.http = builder.baseUrl(props.auth().baseUrl()).build();
    }

    public MeResponse me() {
        return get("/auth/me", new ParameterizedTypeReference<>() {});
    }

    public List<SessionDto> mySessions() {
        return getList("/auth/sessions", new ParameterizedTypeReference<>() {});
    }

    /** Authenticated batch identity resolver — no admin perm required. */
    public List<UserIdentitySummary> userIdentitiesByIds(List<Long> ids) {
        if (ids == null || ids.isEmpty()) return List.of();
        String csv = ids.stream().map(String::valueOf).reduce((a, b) -> a + "," + b).orElse("");
        return getList(uri -> uri.path("/users/by-ids").queryParam("ids", csv).build(),
                new ParameterizedTypeReference<>() {});
    }

    /** Identity-scoped search for the chat people-picker. ACTIVE-only upstream. */
    public List<UserIdentitySummary> userIdentitiesSearch(String q, int size) {
        if (q == null || q.isBlank()) return List.of();
        return getList(uri -> uri.path("/users/search")
                        .queryParam("q", q.trim())
                        .queryParam("size", size).build(),
                new ParameterizedTypeReference<>() {});
    }

    public UserDto adminUser(long userId) {
        return get(uri -> uri.path("/admin/users/{id}").build(userId),
                new ParameterizedTypeReference<>() {});
    }

    public List<SessionDto> adminUserSessions(long userId) {
        return getList(uri -> uri.path("/admin/users/{id}/sessions").build(userId),
                new ParameterizedTypeReference<>() {});
    }

    public LoginHistoryPage adminLoginHistory(long userId, int limit) {
        try {
            LoginHistoryPage body = http.get()
                    .uri(uri -> uri.path("/admin/users/{id}/login-history")
                            .queryParam("limit", limit).build(userId))
                    .retrieve()
                    .body(LoginHistoryPage.class);
            return body == null ? new LoginHistoryPage(List.of(), null) : body;
        } catch (RestClientException ex) {
            throw upstream("admin-login-history", ex);
        }
    }

    public List<RoleDto> adminRoles() {
        return getList("/admin/roles", new ParameterizedTypeReference<>() {});
    }

    public com.forward.hrms.bff.client.dto.PageView<UserDto> adminUsers(String status, int size) {
        try {
            com.forward.hrms.bff.client.dto.PageView<UserDto> body = http.get()
                    .uri(uri -> {
                        var b = uri.path("/admin/users").queryParam("size", size);
                        if (status != null) b.queryParam("status", status);
                        return b.build();
                    })
                    .retrieve()
                    .body(new ParameterizedTypeReference<>() {});
            return body == null
                    ? new com.forward.hrms.bff.client.dto.PageView<>(List.of(), 0, size, 0L, 0, null)
                    : body;
        } catch (RestClientException ex) {
            throw upstream("admin-users", ex);
        }
    }

    /** Null-safe content list for {@link LoginHistoryPage} — was {@code Page.contentOrEmpty()} on the old view. */
    public static List<LoginHistoryEntry> contentOrEmpty(LoginHistoryPage page) {
        if (page == null || page.content() == null) return List.of();
        return page.content();
    }

    private <T> T get(String path, ParameterizedTypeReference<T> type) {
        try {
            return http.get().uri(path).retrieve().body(type);
        } catch (RestClientException ex) {
            throw upstream(path, ex);
        }
    }

    private <T> T get(java.util.function.Function<org.springframework.web.util.UriBuilder, java.net.URI> uri,
                      ParameterizedTypeReference<T> type) {
        try {
            return http.get().uri(uri).retrieve().body(type);
        } catch (RestClientException ex) {
            throw upstream("auth", ex);
        }
    }

    private <T> List<T> getList(String path, ParameterizedTypeReference<List<T>> type) {
        try {
            List<T> body = http.get().uri(path).retrieve().body(type);
            return body == null ? List.of() : body;
        } catch (RestClientException ex) {
            throw upstream(path, ex);
        }
    }

    private <T> List<T> getList(java.util.function.Function<org.springframework.web.util.UriBuilder, java.net.URI> uri,
                                ParameterizedTypeReference<List<T>> type) {
        try {
            List<T> body = http.get().uri(uri).retrieve().body(type);
            return body == null ? List.of() : body;
        } catch (RestClientException ex) {
            throw upstream("auth", ex);
        }
    }

    private static UpstreamError upstream(String what, RestClientException ex) {
        log.warn("auth-service call failed for {}: {}", what, ex.getMessage());
        return new UpstreamError(CommonErrorCode.DEPENDENCY_UNAVAILABLE,
                "auth-service request failed.", ex);
    }
}
