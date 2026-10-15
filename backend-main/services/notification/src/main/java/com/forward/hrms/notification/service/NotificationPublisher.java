package com.forward.hrms.notification.service;

import com.forward.hrms.commons.core.tx.Tx;
import com.forward.hrms.notification.entity.Notification;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import tools.jackson.databind.ObjectMapper;

import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Live-push side of the notification pipeline. The BFF holds the long-lived
 * SSE connections and subscribes to {@code notifications:{userId}}; this
 * component just emits messages onto that channel.
 *
 * <p>Every published row is also handed to {@link PushService} for Web Push
 * delivery to the recipient's subscribed browsers (app closed or not).
 *
 * <p>Also maintains the per-user unread-counter cache used by the bell
 * badge so a counter read doesn't need to scan the DB on every request.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class NotificationPublisher {

    private static final String CHANNEL_PREFIX = "notifications:";
    private static final String UNREAD_COUNT_PREFIX = "notifications:unread:";
    private static final Duration UNREAD_COUNT_TTL = Duration.ofDays(7);

    private final StringRedisTemplate redis;
    private final ObjectMapper json;
    private final PushService push;

    /**
     * Bumps the unread cache and pushes a small JSON envelope to the SSE
     * channel. Both Redis side-effects defer to {@link Tx#afterCommit} so
     * a rollback of the persist tx doesn't leave the bell badge or SSE
     * stream advertising a notification that doesn't exist in the DB.
     */
    public void publish(Notification n) {
        long userId = n.getUserId();
        String message = toMessage(n);
        Tx.afterCommit(() -> {
            incrementUnread(userId);
            redis.convertAndSend(channel(userId), message);
        });
        push.dispatch(java.util.List.of(n));
    }

    /**
     * Pipelined fan-out for {@link NotificationService#dispatchBulk}.
     * One round-trip per batch: increments every recipient's unread
     * counter and fires the SSE channel publish. Batched inside the
     * same Tx.afterCommit so the caller gets the same rollback safety
     * as the single-recipient publish.
     */
    public void publishBulk(java.util.List<Notification> rows) {
        if (rows == null || rows.isEmpty()) return;
        java.util.List<long[]> userIdMessages = new java.util.ArrayList<>(rows.size());
        java.util.List<String> messages = new java.util.ArrayList<>(rows.size());
        for (Notification n : rows) {
            userIdMessages.add(new long[]{n.getUserId()});
            messages.add(toMessage(n));
        }
        Tx.afterCommit(() -> redis.executePipelined((org.springframework.data.redis.core.RedisCallback<Object>) connection -> {
            for (int i = 0; i < userIdMessages.size(); i++) {
                long uid = userIdMessages.get(i)[0];
                byte[] keyBytes = (UNREAD_COUNT_PREFIX + uid).getBytes(java.nio.charset.StandardCharsets.UTF_8);
                connection.commands().incr(keyBytes);
                // TTL is reapplied here because we can't easily know if the key
                // is fresh inside the pipeline; expire is idempotent.
                connection.commands().expire(keyBytes, UNREAD_COUNT_TTL.toSeconds());
                connection.commands().publish(
                        (CHANNEL_PREFIX + uid).getBytes(java.nio.charset.StandardCharsets.UTF_8),
                        messages.get(i).getBytes(java.nio.charset.StandardCharsets.UTF_8));
            }
            return null;
        }));
        push.dispatch(rows);
    }

    public void incrementUnread(long userId) {
        String key = unreadKey(userId);
        Long updated = redis.opsForValue().increment(key);
        if (updated != null && updated == 1L) {
            redis.expire(key, UNREAD_COUNT_TTL);
        }
    }

    public void decrementUnread(long userId, long by) {
        if (by <= 0) return;
        String key = unreadKey(userId);
        Long updated = redis.opsForValue().decrement(key, by);
        if (updated != null && updated < 0) {
            redis.delete(key);
        }
    }

    public void resetUnread(long userId) {
        redis.delete(unreadKey(userId));
    }

    public void setUnread(long userId, long count) {
        redis.opsForValue().set(unreadKey(userId), Long.toString(count), UNREAD_COUNT_TTL);
    }

    public Long readUnread(long userId) {
        String v = redis.opsForValue().get(unreadKey(userId));
        if (v == null) return null;
        try { return Long.parseLong(v); } catch (NumberFormatException e) { return null; }
    }

    private String channel(long userId) { return CHANNEL_PREFIX + userId; }
    private String unreadKey(long userId) { return UNREAD_COUNT_PREFIX + userId; }

    private String toMessage(Notification n) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("id", n.getId());
        body.put("userId", n.getUserId());
        body.put("type", n.getType());
        body.put("title", n.getTitle());
        body.put("body", n.getBody());
        body.put("link", n.getLink());
        if (n.getCategory() != null) body.put("category", n.getCategory().name());
        body.put("priority", n.getPriority().name());
        body.put("createdAt", n.getCreatedAt() == null ? null : n.getCreatedAt().toString());
        try {
            return json.writeValueAsString(body);
        } catch (Exception e) {
            log.warn("Failed to serialize notification {} for SSE publish: {}", n.getId(), e.getMessage());
            return "{\"id\":" + n.getId() + ",\"userId\":" + n.getUserId() + "}";
        }
    }
}
