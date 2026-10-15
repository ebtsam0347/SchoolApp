package com.forward.hrms.notification.service;

import com.forward.hrms.commons.core.tx.Tx;
import com.forward.hrms.notification.config.NotificationProperties;
import com.forward.hrms.notification.entity.ProcessedEvent;
import com.forward.hrms.notification.repository.ProcessedEventRepository;

import lombok.RequiredArgsConstructor;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

/**
 * Two-tier idempotency for Kafka consumers — Redis for the hot path,
 * the {@code notification_processed_events} table as the durable backstop.
 * The Redis check protects against the cheap, common case of redelivery
 * inside the dedupe-ttl window; the table catches anything that slipped
 * past Redis (cache eviction, restart, redrive after the TTL elapsed).
 */
@Component
@RequiredArgsConstructor
public class EventDedupeStore {

    private static final String PREFIX = "notif:eid:";

    private final StringRedisTemplate redis;
    private final ProcessedEventRepository repo;
    private final NotificationProperties props;

    /**
     * Atomically claim {@code eventId} if not seen.
     *
     * <p>Order matters: durable INSERT first, Redis SET in
     * {@link Tx#afterCommit}. The previous "Redis SET first" sequence
     * left the eventId burned in Redis for {@code dedupeTtl} (24h)
     * whenever the surrounding consumer transaction rolled back —
     * permanent silent message loss on the next redelivery. Now Redis
     * gets stamped only after the row actually commits; a rollback
     * leaves both Redis and DB clean and the next redelivery is
     * processed normally.
     *
     * @return true when the caller should process the event, false when
     *         it's a duplicate.
     */
    @Transactional
    public boolean claim(String eventId, String topic, String eventType) {
        if (eventId == null || eventId.isBlank()) {
            // No id → skip dedupe and process. This is the safer default
            // when a producer omits the envelope id (rare, but possible).
            return true;
        }
        String key = PREFIX + eventId;
        // Fast path — cache hit means we already saw this id.
        if (Boolean.TRUE.equals(redis.hasKey(key))) {
            return false;
        }
        // Durable check — Redis cache may have evicted past the TTL.
        if (repo.existsByEventId(eventId)) {
            return false;
        }
        ProcessedEvent p = new ProcessedEvent();
        p.setEventId(eventId);
        p.setSourceTopic(topic == null ? "" : topic);
        p.setEventType(eventType == null ? "" : eventType);
        p.setProcessedAt(LocalDateTime.now());
        try {
            repo.save(p);
        } catch (DataIntegrityViolationException dup) {
            // Another consumer thread won the race; treat as duplicate.
            return false;
        }
        // Stamp Redis only after the row commits; on rollback, Redis
        // stays clean and the next redelivery succeeds.
        Tx.afterCommit(() -> redis.opsForValue().set(key, "1", props.dedupeTtl()));
        return true;
    }
}
