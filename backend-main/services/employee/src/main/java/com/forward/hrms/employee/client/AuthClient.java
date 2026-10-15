package com.forward.hrms.employee.client;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatusCode;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.forward.hrms.commons.core.error.CommonErrorCode;
import com.forward.hrms.commons.core.error.UpstreamError;
import com.forward.hrms.commons.web.security.IdentityHeaders;
import com.forward.hrms.employee.config.EmployeeDownstreamProperties;

import lombok.extern.slf4j.Slf4j;

@Slf4j
@Component
public class AuthClient {

    private final RestClient http;

    public AuthClient(EmployeeDownstreamProperties props, RestClient.Builder builder) {
        this.http = builder.baseUrl(props.auth().baseUrl()).build();
    }

    public CreateUserResponse createUser(CreateUserRequest req) {
        return createUser(req, null, null);
    }

    /**
     * @param identity        identity headers to forward (use
     *                        {@link #currentRequestIdentity()} from a request thread).
     * @param idempotencyKey  optional; when set, auth-service treats a same-key
     *                        retry as 200 with the existing user instead of 409.
     */
    public CreateUserResponse createUser(CreateUserRequest req,
                                         Map<String, String> identity,
                                         String idempotencyKey) {
        try {
            return http.post()
                    .uri("/admin/users")
                    .headers(h -> applyHeaders(h, identity, idempotencyKey))
                    .body(req)
                    .retrieve()
                    .body(CreateUserResponse.class);
        } catch (RestClientException ex) {
            log.warn("auth-service createUser failed for username={}: {}", req.username(), ex.getMessage());
            throw upstream("createUser", ex);
        }
    }

    public void disableUser(long userId) {
        disableUser(userId, null, null);
    }

    public void disableUser(long userId, Map<String, String> identity) {
        disableUser(userId, identity, null);
    }

    /**
     * @param reason flows into auth-service's session-revoke RevokeReason and
     *               the {@code auth.user.deactivated} outbox event. Pass
     *               {@code "EMPLOYEE_TERMINATED"} from the termination saga;
     *               leave {@code null} for the admin-disable / compensation
     *               default ({@code ADMIN_REVOKE}).
     */
    public void disableUser(long userId, Map<String, String> identity, String reason) {
        try {
            http.post()
                    .uri("/admin/users/{id}/disable", userId)
                    .headers(h -> applyHeaders(h, identity, null))
                    .body(reason == null ? Map.of() : Map.of("reason", reason))
                    .retrieve()
                    .toBodilessEntity();
        } catch (RestClientException ex) {
            log.warn("auth-service disableUser failed for userId={}: {}", userId, ex.getMessage());
            throw upstream("disableUser", ex);
        }
    }

    public void enableUser(long userId, Map<String, String> identity) {
        try {
            http.patch()
                    .uri("/admin/users/{id}", userId)
                    .headers(h -> applyHeaders(h, identity, null))
                    .body(new UpdateUserStatusRequest("ACTIVE"))
                    .retrieve()
                    .toBodilessEntity();
        } catch (RestClientException ex) {
            log.warn("auth-service enableUser failed for userId={}: {}", userId, ex.getMessage());
            throw upstream("enableUser", ex);
        }
    }

    /**
     * Snapshots {@code X-User-*} headers from the active request — call from
     * a controller / service in a request-bound thread. Returns an empty
     * map outside a request.
     */
    public static Map<String, String> currentRequestIdentity() {
        Map<String, String> result = new LinkedHashMap<>();
        Optional.ofNullable(RequestContextHolder.getRequestAttributes())
                .filter(ServletRequestAttributes.class::isInstance)
                .map(ServletRequestAttributes.class::cast)
                .map(ServletRequestAttributes::getRequest)
                .ifPresent(req -> {
                    for (String name : IdentityHeaders.ALL) {
                        String value = req.getHeader(name);
                        if (value != null && !value.isBlank()) result.put(name, value);
                    }
                });
        return result;
    }

    private static void applyHeaders(HttpHeaders h, Map<String, String> identity, String idempotencyKey) {
        h.setContentType(org.springframework.http.MediaType.APPLICATION_JSON);
        if (identity != null) identity.forEach(h::set);
        if (idempotencyKey != null && !idempotencyKey.isBlank()) {
            h.set("Idempotency-Key", idempotencyKey);
        }
    }

    private static UpstreamError upstream(String op, RestClientException ex) {
        return new UpstreamError(CommonErrorCode.DEPENDENCY_UNAVAILABLE,
                "auth-service " + op + " failed.", ex);
    }

    /**
     * Saga steps inspect the cause to choose RETRY (5xx / IO) vs FAILED
     * (4xx). Returns the upstream HTTP status when one is available.
     */
    public static Optional<HttpStatusCode> statusOf(Throwable t) {
        Throwable cur = t;
        while (cur != null) {
            if (cur instanceof RestClientResponseException rcre) return Optional.of(rcre.getStatusCode());
            if (cur instanceof HttpClientErrorException hce) return Optional.of(hce.getStatusCode());
            cur = cur.getCause();
        }
        return Optional.empty();
    }

    /** Mirrors auth-service's CreateUserRequest. */
    public record CreateUserRequest(
            String username,
            String fullName,
            String email,
            String temporaryPassword,
            boolean employeeLinked,
            Set<String> roles
    ) {}

    /**
     * Mirrors auth-service's UpdateUserRequest (used here only to flip
     * {@code status} back to ACTIVE — used by {@code TerminationUndoSaga}).
     */
    public record UpdateUserStatusRequest(String status) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record CreateUserResponse(long id, String username, String fullName, String email) {}
}
