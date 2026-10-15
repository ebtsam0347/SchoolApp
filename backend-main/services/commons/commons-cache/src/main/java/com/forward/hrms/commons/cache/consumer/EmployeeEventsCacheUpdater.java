package com.forward.hrms.commons.cache.consumer;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.forward.hrms.commons.cache.CatalogCache;
import com.forward.hrms.commons.cache.EmployeeDisplayCache;
import com.forward.hrms.commons.cache.config.CommonsCacheProperties;
import com.forward.hrms.commons.cache.internal.CacheKeys;
import com.forward.hrms.commons.employeeevents.EmployeeEvents;
import com.forward.hrms.commons.employeeevents.EmployeeEventsConsumerSupport;

import lombok.extern.slf4j.Slf4j;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.kafka.annotation.KafkaListener;

/**
 * Keeps the shared Redis cache in sync with employee-service mutations.
 * Each cache-using service runs its own consumer instance with a
 * service-scoped group id (so every service converges to the same
 * Redis state — idempotent writes).
 *
 * <h2>What it does</h2>
 * <ul>
 *   <li>{@code employee.created / updated / *_changed} → invalidate
 *       the employee key. We deliberately don't write the new value
 *       inline: the producer's payload doesn't carry every display
 *       field (no profilePictureUrl, no resolved catalog names), and
 *       fetching them here means re-doing the catalog joins. Cheaper
 *       to invalidate and let the next render hit the bulk-fetch path
 *       once.</li>
 *   <li>{@code employee.terminated} → tombstone (short TTL) so any
 *       lingering reference doesn't keep refetching.</li>
 *   <li>Other event types → no-op (the cache doesn't track suspension
 *       state, ESS state, etc. — those are not display fields).</li>
 * </ul>
 *
 * <p>The group id is configured via {@code hrms.cache.consumer-group}
 * (defaults to {@code <spring.application.name>.cache-updater}) so
 * each service's consumer reads independently.
 */
@Slf4j
public class EmployeeEventsCacheUpdater extends EmployeeEventsConsumerSupport {

    private final EmployeeDisplayCache cache;
    private final CatalogCache catalogs;

    public EmployeeEventsCacheUpdater(EmployeeDisplayCache cache,
                                      CatalogCache catalogs,
                                      CommonsCacheProperties props,
                                      @Qualifier("outboxObjectMapper") ObjectMapper json) {
        super(json);
        this.cache = cache;
        this.catalogs = catalogs;
    }

    @KafkaListener(
            topics = EmployeeEvents.TOPIC,
            groupId = "${hrms.cache.consumer-group:${spring.application.name}.cache-updater}")
    public void onMessage(String message) throws java.io.IOException {
        dispatch(message);
    }

    @Override
    protected void handle(String eventType, JsonNode payload) {
        switch (eventType) {
            case EmployeeEvents.EMPLOYEE_CREATED,
                 EmployeeEvents.EMPLOYEE_UPDATED,
                 EmployeeEvents.EMPLOYEE_MANAGER_CHANGED,
                 EmployeeEvents.EMPLOYEE_DEPARTMENT_CHANGED,
                 EmployeeEvents.EMPLOYEE_SECTION_CHANGED,
                 EmployeeEvents.EMPLOYEE_DESIGNATION_CHANGED,
                 EmployeeEvents.EMPLOYEE_GRADE_CHANGED,
                 EmployeeEvents.EMPLOYEE_CONFIRMED -> invalidateOne(payload);

            case EmployeeEvents.EMPLOYEE_TERMINATED -> tombstoneOne(payload);

            // Catalog rename / catalog mutation events don't exist on the
            // employee.events topic yet (see docs/caching.md "extension
            // points"). When they ship, switch on them here and call
            // catalogs.invalidate(CacheKeys.CAT_<thing>).

            default -> { /* not a display-field change */ }
        }
    }

    private void invalidateOne(JsonNode payload) {
        Long id = readLong(payload, "employeeId");
        if (id == null) return;
        cache.invalidate(java.util.List.of(id));
    }

    private void tombstoneOne(JsonNode payload) {
        Long id = readLong(payload, "employeeId");
        if (id == null) return;
        cache.tombstone(id);
    }

    /** Suppresses an unused-warning until catalog event consumers ship. */
    @SuppressWarnings("unused")
    private void invalidateCatalog(String key) {
        catalogs.invalidate(key);
        // Reference CacheKeys to keep the import live for future catalog handling.
        if (key != null && key.startsWith(CacheKeys.CAT)) log.trace("invalidated catalog {}", key);
    }
}
