package com.forward.hrms.auth.client;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.springframework.http.HttpStatusCode;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.util.UriBuilder;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.forward.hrms.auth.config.AuthDownstreamProperties;
import com.forward.hrms.commons.core.error.CommonErrorCode;
import com.forward.hrms.commons.core.error.UpstreamError;

import lombok.extern.slf4j.Slf4j;

@Slf4j
@Component
public class AuditClient {

    private final RestClient http;

    public AuditClient(AuthDownstreamProperties props, RestClient.Builder builder) {
        this.http = builder.baseUrl(props.audit().baseUrl()).build();
    }

    public AuditPage byEntity(String entityType, String entityId,
                              LocalDateTime from, LocalDateTime to, int limit, String cursor) {
        try {
            AuditPage body = http.get()
                    .uri(uri -> withRange(withCursor(uri, cursor), from, to, limit)
                            .path("/audit/by-entity/{type}/{id}")
                            .build(entityType, entityId))
                    .retrieve()
                    .body(AuditPage.class);
            return body == null ? new AuditPage(List.of(), null) : body;
        } catch (HttpClientErrorException.NotFound ex) {
            return new AuditPage(List.of(), null);
        } catch (RestClientException ex) {
            HttpStatusCode status = (ex instanceof HttpClientErrorException ce) ? ce.getStatusCode() : null;
            log.warn("audit-service call failed for {}:{} ({}): {}",
                    entityType, entityId, status, ex.getMessage());
            throw new UpstreamError(CommonErrorCode.DEPENDENCY_UNAVAILABLE,
                    "audit-service request failed.", ex);
        }
    }

    private static UriBuilder withRange(UriBuilder uri, LocalDateTime from, LocalDateTime to, int limit) {
        Optional.ofNullable(from).ifPresent(v -> uri.queryParam("from", v.toString()));
        Optional.ofNullable(to).ifPresent(v -> uri.queryParam("to", v.toString()));
        if (limit > 0) uri.queryParam("limit", limit);
        return uri;
    }

    private static UriBuilder withCursor(UriBuilder uri, String cursor) {
        if (cursor != null && !cursor.isBlank()) uri.queryParam("cursor", cursor);
        return uri;
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record AuditEvent(
            String action,
            String actorUsername,
            String ipAddress,
            Map<String, Object> metadata,
            Map<String, Object> after,
            String message,
            LocalDateTime occurredAt
    ) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record AuditPage(List<AuditEvent> content, String nextCursor) {}
}
