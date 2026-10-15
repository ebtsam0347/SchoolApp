package com.forward.hrms.bff.service;

import com.forward.hrms.bff.client.ChatClient;
import com.forward.hrms.bff.config.StreamProperties;

import lombok.extern.slf4j.Slf4j;

import org.springframework.data.redis.connection.MessageListener;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.listener.ChannelTopic;
import org.springframework.data.redis.listener.RedisMessageListenerContainer;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.JsonNode;

/**
 * One {@link SseEmitter} per browser tab the user has open. Each emitter
 * subscribes to two per-user Redis pub/sub channels:
 *
 * <ul>
 *   <li>{@code notifications:{userId}} — the bell feed; forwarded as
 *       {@code notification} SSE events.</li>
 *   <li>{@code chat:{userId}} — chat-service live signals; the inbound
 *       payload carries a {@code "type"} field (e.g. {@code chat.message},
 *       {@code chat.message.edited}, {@code chat.message.deleted},
 *       {@code chat.message.delivered}, {@code chat.read},
 *       {@code chat.typing}); we forward each as an SSE event of that
 *       same name so the SPA dispatches off it.</li>
 * </ul>
 *
 * <p>The SPA invalidates relevant React-Query keys on receipt — the
 * message body is enough to know which (it includes {@code userId} for
 * notifications and a {@code conversationId}/{@code messageId} for chat).
 */
@Slf4j
@Service
public class NotificationStreamService {

    private static final String NOTIFICATION_CHANNEL_PREFIX = "notifications:";
    private static final String CHAT_CHANNEL_PREFIX = "chat:";
    /** Global org-wide broadcast channel — every SSE emitter subscribes. */
    private static final String CHAT_BROADCAST_CHANNEL = "chat:broadcast";
    /** Presence keys + the global presence:events channel. */
    private static final String PRESENCE_KEY_PREFIX = "presence:";
    private static final String PRESENCE_LASTSEEN_PREFIX = "presence:lastseen:";
    private static final String PRESENCE_EVENTS_CHANNEL = "presence:events";
    private static final Duration PRESENCE_TTL = Duration.ofSeconds(30);

    private final RedisMessageListenerContainer container;
    private final StringRedisTemplate redis;
    private final ChatClient chatClient;
    private final StreamProperties props;
    private final ObjectMapper json = new ObjectMapper();

    /**
     * Active connections — keyed by an opaque connection id (not userId,
     * since one user can have many tabs). Each entry holds the emitter
     * + the Redis listener registered for it, so we can unsubscribe on
     * cleanup without scanning all listeners on the container.
     */
    private final Map<String, Connection> connections = new ConcurrentHashMap<>();

    public NotificationStreamService(RedisMessageListenerContainer container,
                                      StringRedisTemplate redis,
                                      ChatClient chatClient,
                                      StreamProperties props) {
        this.container = container;
        this.redis = redis;
        this.chatClient = chatClient;
        this.props = props;
    }

    public SseEmitter open(long userId) {
        SseEmitter emitter = new SseEmitter(props.timeout().toMillis());
        String connectionId = UUID.randomUUID().toString();

        ChannelTopic notifTopic = ChannelTopic.of(NOTIFICATION_CHANNEL_PREFIX + userId);
        ChannelTopic chatTopic = ChannelTopic.of(CHAT_CHANNEL_PREFIX + userId);
        ChannelTopic chatBroadcastTopic = ChannelTopic.of(CHAT_BROADCAST_CHANNEL);
        ChannelTopic presenceTopic = ChannelTopic.of(PRESENCE_EVENTS_CHANNEL);

        MessageListener notifListener = (message, pattern) -> {
            String body = new String(message.getBody(), StandardCharsets.UTF_8);
            try {
                emitter.send(SseEmitter.event()
                        .name("notification")
                        .data(body));
            } catch (IOException e) {
                log.debug("SSE notification send failed for connection {} (user {}): {}",
                        connectionId, userId, e.getMessage());
                emitter.completeWithError(e);
            }
        };
        MessageListener chatListener = (message, pattern) -> {
            String body = new String(message.getBody(), StandardCharsets.UTF_8);
            String eventName = readType(body, "chat.message");
            boolean sent = false;
            try {
                emitter.send(SseEmitter.event()
                        .name(eventName)
                        .data(body));
                sent = true;
            } catch (IOException e) {
                log.debug("SSE chat send failed for connection {} (user {}): {}",
                        connectionId, userId, e.getMessage());
                emitter.completeWithError(e);
            }
            // ACK delivery to chat-service so the sender can flip to ✓✓.
            // Only on the original "chat.message" type — edits/deletes/reads
            // /typings shouldn't bump delivery.
            if (sent && "chat.message".equals(eventName)) {
                Long messageId = readLong(body, "messageId");
                Long sender = readLong(body, "senderUserId");
                if (messageId != null && sender != null && sender != userId) {
                    chatClient.markDelivered(messageId, userId);
                }
            }
        };
        MessageListener presenceListener = (message, pattern) -> {
            String body = new String(message.getBody(), StandardCharsets.UTF_8);
            try {
                emitter.send(SseEmitter.event().name("presence.changed").data(body));
            } catch (IOException e) {
                log.debug("SSE presence send failed for connection {} (user {}): {}",
                        connectionId, userId, e.getMessage());
                emitter.completeWithError(e);
            }
        };

        container.addMessageListener(notifListener, notifTopic);
        container.addMessageListener(chatListener, chatTopic);
        // Broadcast forwarder reuses the chatListener — same payload shape;
        // org-wide announcements arrive on a fixed channel so a single publish
        // reaches every connected user.
        container.addMessageListener(chatListener, chatBroadcastTopic);
        container.addMessageListener(presenceListener, presenceTopic);
        connections.put(connectionId, new Connection(userId, emitter,
                List.of(
                        new Subscription(notifListener, notifTopic),
                        new Subscription(chatListener, chatTopic),
                        new Subscription(chatListener, chatBroadcastTopic),
                        new Subscription(presenceListener, presenceTopic))));

        // Presence write — first SSE attach for this user is a transition
        // to "online". Other tabs/connections just refresh the TTL.
        Boolean firstAttach = redis.opsForValue().setIfAbsent(
                PRESENCE_KEY_PREFIX + userId, LocalDateTime.now().toString(), PRESENCE_TTL);
        if (firstAttach == null || firstAttach) {
            // Either no existing presence (firstAttach==true) or transition
            // back from offline → publish presence.changed.
            redis.opsForValue().set(PRESENCE_LASTSEEN_PREFIX + userId, LocalDateTime.now().toString());
            redis.convertAndSend(PRESENCE_EVENTS_CHANNEL,
                    "{\"userId\":" + userId + ",\"status\":\"online\",\"at\":\""
                            + LocalDateTime.now() + "\"}");
        } else {
            // Refresh the TTL (a tab opening when another is already alive).
            redis.expire(PRESENCE_KEY_PREFIX + userId, PRESENCE_TTL);
            redis.opsForValue().set(PRESENCE_LASTSEEN_PREFIX + userId, LocalDateTime.now().toString());
        }

        Runnable cleanup = () -> {
            Connection removed = connections.remove(connectionId);
            if (removed != null) {
                for (Subscription s : removed.subscriptions()) {
                    container.removeMessageListener(s.listener(), s.topic());
                }
            }
        };
        emitter.onCompletion(cleanup);
        emitter.onTimeout(() -> { emitter.complete(); cleanup.run(); });
        emitter.onError(t -> { emitter.completeWithError(t); cleanup.run(); });

        // Send an initial "ready" event so the client knows the connection
        // is established before the first notification arrives. Some clients
        // also use this to clear any "reconnecting" UI state.
        try {
            emitter.send(SseEmitter.event().name("ready").data("{\"userId\":" + userId + "}"));
        } catch (IOException e) {
            log.debug("Initial ready event failed for {}: {}", connectionId, e.getMessage());
            cleanup.run();
            emitter.completeWithError(e);
        }

        log.debug("SSE connection {} opened for user {} (active: {})",
                connectionId, userId, connections.size());
        return emitter;
    }

    /**
     * Periodic comment-line heartbeat. Browsers and reverse proxies time
     * out idle connections; an SSE comment ({@code :keepalive}) keeps the
     * socket warm without delivering a real event to the client.
     *
     * <p>15 s sits comfortably under the 30–60 s default-idle on most
     * reverse proxies. Hardcoded — making one constant configurable
     * isn't worth the SpEL wiring, and the comment cadence isn't
     * environment-sensitive the way the per-connection timeout is.
     */
    @Scheduled(fixedDelay = 15_000L)
    public void heartbeat() {
        if (connections.isEmpty()) return;
        LocalDateTime now = LocalDateTime.now();
        // Distinct user ids — one tab is enough to keep presence alive.
        java.util.Set<Long> liveUsers = new java.util.HashSet<>();
        connections.forEach((id, c) -> {
            try {
                c.emitter().send(SseEmitter.event().comment("keepalive"));
                liveUsers.add(c.userId());
            } catch (Exception e) {
                // The emitter's onError will fire and reap the entry; safe
                // to ignore here.
                log.trace("Heartbeat failed for connection {}: {}", id, e.getMessage());
            }
        });
        // Refresh presence TTL + last-seen for every live user.
        String nowStr = now.toString();
        for (Long uid : liveUsers) {
            try {
                redis.opsForValue().set(PRESENCE_KEY_PREFIX + uid, nowStr, PRESENCE_TTL);
                redis.opsForValue().set(PRESENCE_LASTSEEN_PREFIX + uid, nowStr);
            } catch (Exception e) {
                log.trace("Presence heartbeat failed for user {}: {}", uid, e.getMessage());
            }
        }
    }

    /** Active-connection count, exposed for tests / debugging. */
    public int activeConnections() {
        return connections.size();
    }

    /** Pull the {@code "type"} field out of a chat envelope; fall back to a default. */
    private String readType(String json, String fallback) {
        try {
            JsonNode node = this.json.readTree(json);
            JsonNode t = node.get("type");
            return t != null && t.isTextual() ? t.asString() : fallback;
        } catch (Exception e) {
            return fallback;
        }
    }

    /** Pull a numeric/text-numeric field out of a chat envelope. */
    private Long readLong(String json, String field) {
        try {
            JsonNode node = this.json.readTree(json);
            JsonNode v = node.get(field);
            if (v == null || v.isNull()) return null;
            if (v.isNumber()) return v.asLong();
            if (v.isTextual() && !v.asString().isBlank()) {
                try { return Long.parseLong(v.asString()); } catch (NumberFormatException ignored) { return null; }
            }
            return null;
        } catch (Exception e) {
            return null;
        }
    }

    private record Connection(long userId, SseEmitter emitter, List<Subscription> subscriptions) {}
    private record Subscription(MessageListener listener, ChannelTopic topic) {}
}
