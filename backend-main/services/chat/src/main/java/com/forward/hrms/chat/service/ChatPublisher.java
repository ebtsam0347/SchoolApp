package com.forward.hrms.chat.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import tools.jackson.databind.ObjectMapper;

import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Live-push side of chat. Each conversation member (excluding the actor)
 * has a Redis pub/sub channel {@code chat:{userId}} that the BFF SSE
 * forwarder is subscribed to. We publish here from a transaction's
 * {@code afterCommit} hook so a Redis blip never rolls back a committed
 * write — the durable path is the outbox (Kafka) plus the next message
 * fetch reconciling state.
 *
 * <p>The published payload is a small JSON envelope: {@code type}
 * (event name like {@code chat.message}, {@code chat.message.edited},
 * {@code chat.message.deleted}, {@code chat.read}, {@code chat.typing},
 * {@code chat.message.delivered}) plus a flat data object. The SPA
 * dispatches on {@code type}.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ChatPublisher {

    private static final String CHANNEL_PREFIX = "chat:";
    /**
     * Global broadcast channel — every BFF SSE emitter subscribes to it,
     * so a single publish reaches every connected user. Used for
     * announcements that go to "everyone".
     */
    public static final String BROADCAST_CHANNEL = "chat:broadcast";

    private final StringRedisTemplate redis;
    private final ObjectMapper json;

    public void publishToUser(long userId, String type, Map<String, Object> data) {
        String body = serialize(type, data);
        redis.convertAndSend(CHANNEL_PREFIX + userId, body);
    }

    /**
     * Pipelined fan-out — one round-trip per batch instead of N. Spring
     * Data Redis' {@code executePipelined} buffers commands, ships them
     * in one network call, and reads the replies in order. Pub/sub
     * delivery is at-most-once either way; the win is removing the
     * sequential RTT cost when a chat message hits a 50-person room.
     */
    public void publishToUsers(Collection<Long> userIds, String type, Map<String, Object> data) {
        if (userIds == null || userIds.isEmpty()) return;
        byte[] body = serialize(type, data).getBytes(java.nio.charset.StandardCharsets.UTF_8);
        java.util.List<Long> targets = userIds.stream()
                .filter(java.util.Objects::nonNull)
                .distinct()
                .toList();
        if (targets.isEmpty()) return;
        redis.executePipelined((org.springframework.data.redis.core.RedisCallback<Object>) connection -> {
            for (Long uid : targets) {
                byte[] channel = (CHANNEL_PREFIX + uid).getBytes(java.nio.charset.StandardCharsets.UTF_8);
                connection.publish(channel, body);
            }
            return null;
        });
    }

    /** Publish to a fixed channel (e.g. {@link #BROADCAST_CHANNEL}). */
    public void publishToChannel(String channel, String type, Map<String, Object> data) {
        redis.convertAndSend(channel, serialize(type, data));
    }

    private String serialize(String type, Map<String, Object> data) {
        Map<String, Object> envelope = new LinkedHashMap<>();
        envelope.put("type", type);
        if (data != null && !data.isEmpty()) envelope.putAll(data);
        try {
            return json.writeValueAsString(envelope);
        } catch (Exception e) {
            log.warn("Failed to serialize chat envelope ({}): {}", type, e.getMessage());
            return "{\"type\":\"" + type + "\"}";
        }
    }
}
