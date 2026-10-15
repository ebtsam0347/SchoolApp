package com.forward.hrms.commons.saga;

import java.time.Duration;
import java.util.Map;

/**
 * Storage for per-saga secrets that must NOT land in
 * {@code saga_state.payload}. The payload is jsonb in Postgres and is
 * exposed by {@code GET /sagas/{id}} for operator inspection — anything
 * sensitive (temporary passwords, API keys, signed-PUT tokens) belongs
 * here instead.
 *
 * <p>The default implementation is Redis-backed
 * ({@code RedisSagaSecretStore}, autoconfigured when
 * {@code spring-data-redis} is on the classpath). Services that want
 * sagas without secrets can omit Redis entirely; the autoconfig
 * silently does not register a bean and {@code SagaService.start}
 * (no-secrets path) keeps working. {@code startWithSecrets} requires
 * a store and fails fast at startup otherwise.
 */
public interface SagaSecretStore {

    /**
     * Store {@code secrets} for {@code sagaId} with the given TTL. The
     * TTL should bound the saga's max-life so the secrets disappear
     * even if the saga lingers in {@code FAILED} forever.
     */
    void put(long sagaId, Map<String, String> secrets, Duration ttl);

    /** Look up a single secret by name. Returns {@code null} when missing. */
    String get(long sagaId, String name);

    /** Drop every secret for the saga. Called on COMPLETED / FAILED. */
    void remove(long sagaId);
}
