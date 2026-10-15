package com.forward.hrms.bff.client;

import com.forward.hrms.audit.dto.response.AuditEventDto;
import com.forward.hrms.bff.client.dto.PageView;
import com.forward.hrms.audit.dto.response.TimelinePage;
import com.forward.hrms.bff.config.DownstreamProperties;
import com.forward.hrms.commons.core.error.CommonErrorCode;
import com.forward.hrms.commons.core.error.UpstreamError;

import lombok.extern.slf4j.Slf4j;

import org.springframework.core.ParameterizedTypeReference;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Audit-service slice the BFF needs.
 *
 * <ul>
 *   <li>{@link #userTimeline(long, int)} — caller's own user-visible
 *       timeline for the account-summary view.</li>
 *   <li>{@link #adminByActor(long, int)} — admin view of an actor's
 *       recent actions (gated by audit's own {@code audit.read}).</li>
 * </ul>
 */
@Slf4j
@Component
public class AuditClient {

    private final RestClient http;

    public AuditClient(DownstreamProperties props, RestClient.Builder builder) {
        this.http = builder.baseUrl(props.audit().baseUrl()).build();
    }

    /** Top-N user-visible events for the user's own /timeline page. */
    public TimelinePage userTimeline(long userId, int limit) {
        return entityTimeline("user", String.valueOf(userId), limit, null);
    }

    /**
     * Generic entity-timeline read. {@code entityType} / {@code entityId} is the same shape
     * the audit-service uses (e.g. {@code "user" / "1042"}, {@code "employee" / "42"}).
     * Cursor pagination — pass {@code cursor=null} to start at the head.
     */
    public TimelinePage entityTimeline(String entityType, String entityId, int limit, String cursor) {
        try {
            return http.get()
                    .uri(uri -> {
                        var b = uri.path("/timeline/{type}/{id}")
                                .queryParam("limit", limit);
                        if (cursor != null && !cursor.isBlank()) b.queryParam("cursor", cursor);
                        return b.build(entityType, entityId);
                    })
                    .retrieve()
                    .body(TimelinePage.class);
        } catch (RestClientException ex) {
            throw upstream("entity-timeline:" + entityType, ex);
        }
    }

    /**
     * Filtered audit search. Mirrors the upstream {@code /audit/search} contract.
     * Returns an empty page if the caller lacks {@code audit.read} (rather than
     * surfacing the 403 — the dashboard slice degrades to "no anomalies").
     */
    public PageView<AuditEventDto> search(String entityType, String action,
                                           LocalDateTime from, LocalDateTime to, int size) {
        try {
            PageView<AuditEventDto> body = http.get()
                    .uri(uri -> {
                        var b = uri.path("/audit/search")
                                .queryParam("page", 0)
                                .queryParam("size", size);
                        if (entityType != null) b.queryParam("entityType", entityType);
                        if (action != null) b.queryParam("action", action);
                        if (from != null) b.queryParam("from", from.toString());
                        if (to != null) b.queryParam("to", to.toString());
                        return b.build();
                    })
                    .retrieve()
                    .body(new ParameterizedTypeReference<>() {});
            return body == null ? new PageView<>(List.of(), 0, size, 0L, 0, null) : body;
        } catch (HttpClientErrorException.Forbidden ex) {
            return new PageView<>(List.of(), 0, size, 0L, 0, null);
        } catch (RestClientException ex) {
            throw upstream("search", ex);
        }
    }

    /**
     * Audit consumer health snapshot. Returns null when the caller lacks
     * {@code audit.admin}, so the admin dashboard can surface zeros without
     * propagating a 403.
     */
    public tools.jackson.databind.JsonNode healthOrNull() {
        try {
            return http.get()
                    .uri("/admin/audit/health")
                    .retrieve()
                    .body(tools.jackson.databind.JsonNode.class);
        } catch (HttpClientErrorException.Forbidden ex) {
            return null;
        } catch (RestClientException ex) {
            throw upstream("admin-health", ex);
        }
    }

    /** Admin view: recent actions taken BY a given user across services. */
    public PageView<AuditEventDto> adminByActor(long userId, int size) {
        try {
            PageView<AuditEventDto> body = http.get()
                    .uri(uri -> uri.path("/audit/by-actor/{id}")
                            .queryParam("page", 0)
                            .queryParam("size", size)
                            .build(userId))
                    .retrieve()
                    .body(new ParameterizedTypeReference<>() {});
            return body;
        } catch (RestClientException ex) {
            throw upstream("by-actor", ex);
        }
    }

    private static UpstreamError upstream(String what, RestClientException ex) {
        log.warn("audit-service call failed for {}: {}", what, ex.getMessage());
        return new UpstreamError(CommonErrorCode.DEPENDENCY_UNAVAILABLE,
                "audit-service request failed.", ex);
    }
}
