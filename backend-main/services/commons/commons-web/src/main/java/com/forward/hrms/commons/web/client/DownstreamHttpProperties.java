package com.forward.hrms.commons.web.client;

import jakarta.validation.constraints.NotBlank;

import org.hibernate.validator.constraints.URL;

/**
 * Reusable shape for service-to-service base URL configuration. The
 * {@link URL} validator forces a syntactically-valid URL (rules out
 * {@code "asdf"} or an empty string slipping in from a misconfigured
 * env var); {@link NotBlank} forces it to actually be set.
 *
 * <h2>How to use</h2>
 * Per service, declare a {@code @ConfigurationProperties} record
 * embedding this shape:
 *
 * <pre>{@code
 * @ConfigurationProperties("hrms.attendance")
 * @Validated
 * public record AttendanceProperties(DownstreamHttpProperties http) {}
 * }</pre>
 *
 * Application yml:
 * <pre>
 * hrms:
 *   attendance:
 *     http:
 *       base-url: ${ATTENDANCE_BASE_URL:http://localhost:8083}
 * </pre>
 *
 * <p>Defaults that point at {@code localhost} are useful for dev. Per
 * the gateway #21 fix, services should fail fast when a {@code localhost}
 * default is in play in a non-dev profile (k8s deploy missed setting
 * the env var). That check is per-service today; a future
 * {@code DownstreamUrlGuard} can enforce it across all
 * {@link DownstreamHttpProperties} instances at startup.
 */
public record DownstreamHttpProperties(
        @NotBlank @URL String baseUrl
) {}
