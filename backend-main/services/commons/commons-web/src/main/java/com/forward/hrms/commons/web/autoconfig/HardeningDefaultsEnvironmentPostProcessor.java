package com.forward.hrms.commons.web.autoconfig;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.env.EnvironmentPostProcessor;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.core.env.MapPropertySource;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Phase 5 hardening defaults — graceful shutdown, separate management
 * port, actuator surface, tracing endpoint, structured-log toggle.
 *
 * <p>Shipping these as a {@link EnvironmentPostProcessor} (rather than as
 * a packaged {@code application.yml}) is necessary because Spring Boot's
 * classpath-{@code application.yml} lookup picks the first match — each
 * service ships its own, so the commons file would be silently shadowed.
 * Registering the defaults via {@code addLast} puts them at the lowest
 * precedence; any service or env var can override individual keys.
 *
 * <p>If you change a key here, also update {@code docs/common-lib.md}
 * (Phase 5 hardening section).
 */
public class HardeningDefaultsEnvironmentPostProcessor implements EnvironmentPostProcessor {

    private static final String SOURCE_NAME = "fshrms-hardening-defaults";

    @Override
    public void postProcessEnvironment(ConfigurableEnvironment env, SpringApplication app) {
        if (env.getPropertySources().contains(SOURCE_NAME)) return;
        Map<String, Object> d = new LinkedHashMap<>();

        // --- Graceful shutdown (drain in-flight requests; bounded). ---
        d.put("server.shutdown", "graceful");
        d.put("spring.lifecycle.timeout-per-shutdown-phase", "30s");

        // --- Management server on a separate port. ---
        d.put("management.server.port", 9090);

        // --- Actuator surface: probes + prometheus + info. No beans/conditions. ---
        d.put("management.endpoints.web.exposure.include", "health,info,prometheus");
        d.put("management.endpoint.health.probes.enabled", true);
        d.put("management.endpoint.health.show-details", "never");
        d.put("management.endpoint.health.group.liveness.include", "livenessState");
        // `*` picks up whatever health contributors each service actually has —
        // db on services with a DataSource, redis on services with Spring Data
        // Redis, etc. Naming `db` explicitly fails startup on gateway/bff
        // (no DataSource): "Included health contributor 'db' does not exist".
        d.put("management.endpoint.health.group.readiness.include", "*");

        // --- Tracing. Sampling 0.0 by default → silent locally; clusters set 1.0. ---
        d.put("management.tracing.sampling.probability",
                "${MANAGEMENT_TRACING_SAMPLING_PROBABILITY:0.0}");
        d.put("management.zipkin.tracing.endpoint",
                "${HRMS_ZIPKIN_ENDPOINT:http://localhost:9411/api/v2/spans}");

        // --- Structured logging. Empty default → human-readable pattern in dev. ---
        d.put("logging.structured.format.console",
                "${LOGGING_STRUCTURED_FORMAT_CONSOLE:}");

        // --- HikariCP defaults. The Spring Boot default maximum-pool-size=10
        // is too tight once a service runs both an outbox poller, a Kafka
        // listener group, and a @Scheduled REQUIRES_NEW batch (e.g. attendance
        // NightlyCloseJob) on the same thread pool — total connection demand
        // exceeds 10 and the pool stalls with active=10, waiting=N.
        // 20 × 7 JPA services = 140, well under PG max_connections=200.
        //
        // leak-detection-threshold logs the stack trace of the acquiring
        // thread when a connection is held longer than the threshold without
        // being returned to the pool. Set to 60s — replay/recompute ticks
        // comfortably finish under that in healthy operation. Silently
        // ignored on services without a DataSource (gateway, bff). --
        d.put("spring.datasource.hikari.maximum-pool-size",
                "${HIKARI_MAX_POOL_SIZE:20}");
        d.put("spring.datasource.hikari.leak-detection-threshold",
                "${HIKARI_LEAK_DETECTION_THRESHOLD_MS:60000}");
        // Hikari's default minimum-idle equals maximum-pool-size, so 20×7
        // services = 140 connections opened on startup. Local Postgres.app's
        // default max_connections=100 rejects later services with "FATAL:
        // sorry, too many clients already". Setting minimum-idle small lets
        // the pool grow on demand up to the max; at idle each service holds
        // ~5 connections (35 across 7 services, well under both local 100
        // and UAT 200).
        d.put("spring.datasource.hikari.minimum-idle",
                "${HIKARI_MIN_IDLE:5}");

        env.getPropertySources().addLast(new MapPropertySource(SOURCE_NAME, d));
    }
}
