package com.forward.hrms.notification.service;

import com.forward.hrms.commons.core.context.CurrentUserHolder;
import com.forward.hrms.commons.core.error.CommonErrorCode;
import com.forward.hrms.commons.core.error.UnauthorizedError;
import com.forward.hrms.commons.core.error.UnprocessableError;
import com.forward.hrms.commons.core.error.ValidationError;
import com.forward.hrms.commons.core.error.Violation;
import com.forward.hrms.commons.core.tx.Tx;
import com.forward.hrms.notification.config.PushProperties;
import com.forward.hrms.notification.dto.request.PushSubscribeRequest;
import com.forward.hrms.notification.dto.response.PushConfigResponse;
import com.forward.hrms.notification.entity.Notification;
import com.forward.hrms.notification.entity.PushSubscription;
import com.forward.hrms.notification.enums.NotificationErrorCode;
import com.forward.hrms.notification.enums.NotificationPriority;
import com.forward.hrms.notification.push.PushMessage;
import com.forward.hrms.notification.push.VapidSigner;
import com.forward.hrms.notification.push.WebPushClient;
import com.forward.hrms.notification.push.WebPushCrypto;
import com.forward.hrms.notification.repository.PushSubscriptionRepository;

import jakarta.annotation.PreDestroy;

import lombok.extern.slf4j.Slf4j;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import tools.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Semaphore;
import java.util.stream.Collectors;

/**
 * Web Push: browser subscriptions plus delivery of every persisted
 * notification to the recipient's subscribed browsers, so it pops as an OS
 * notification even with the app closed. Complements the SSE bell, which
 * only reaches open tabs.
 *
 * <p>Delivery is fire-and-forget after commit on virtual threads — a slow
 * or failing push service never delays the Kafka consumer. No retries: the
 * notification is still in the bell. Subscriptions the push service reports
 * gone (404/410) are deleted.
 */
@Slf4j
@Service
public class PushService {

    /** Keeps the encrypted message well under the 4 KB push-service limit. */
    private static final int BODY_MAX_CHARS = 500;
    /** Concurrent requests to push services during a broadcast fan-out. */
    private static final int MAX_IN_FLIGHT = 32;

    private final PushProperties props;
    private final PushSubscriptionRepository subscriptions;
    private final ObjectMapper json;
    private final WebPushClient client;
    private final ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor();
    private final Semaphore inFlight = new Semaphore(MAX_IN_FLIGHT);

    public PushService(PushProperties props, PushSubscriptionRepository subscriptions, ObjectMapper json) {
        this.props = props;
        this.subscriptions = subscriptions;
        this.json = json;
        this.client = props.enabled()
                ? new WebPushClient(
                        new VapidSigner(props.publicKey(), props.privateKey(), props.subject()),
                        props.ttl(), props.allowedHosts())
                : null;
        if (client == null) log.info("Web Push disabled — hrms.notification.push keys not set");
    }

    @PreDestroy
    void shutdown() {
        executor.close();
    }

    public PushConfigResponse config() {
        return client == null
                ? new PushConfigResponse(false, null)
                : new PushConfigResponse(true, props.publicKey().trim());
    }

    /**
     * Register (or re-bind to the caller) a browser subscription. The
     * endpoint is unique, so a second user signing in on the same browser
     * takes the row over — the previous user stops getting pushes there.
     */
    @Transactional
    public void subscribe(PushSubscribeRequest req) {
        if (client == null) {
            throw new UnprocessableError(NotificationErrorCode.PUSH_NOT_CONFIGURED, "web push is not configured");
        }
        if (!client.isAllowedEndpoint(req.endpoint())) {
            throw new ValidationError(NotificationErrorCode.PUSH_ENDPOINT_NOT_ALLOWED, "unsupported push service",
                    List.of(new Violation("endpoint", "NOT_ALLOWED", "not a known browser push service")));
        }
        if (!WebPushCrypto.isValidPublicKey(req.p256dh()) || !isValidAuth(req.auth())) {
            throw new ValidationError(NotificationErrorCode.PUSH_KEYS_INVALID, "invalid subscription keys",
                    List.of(new Violation("p256dh", "INVALID", "not a P-256 public key, or auth is not 16 bytes")));
        }
        long userId = currentUserId();
        PushSubscription sub = subscriptions.findByEndpoint(req.endpoint()).orElseGet(PushSubscription::new);
        sub.setUserId(userId);
        sub.setEndpoint(req.endpoint());
        sub.setP256dh(req.p256dh());
        sub.setAuth(req.auth());
        sub.setUserAgent(req.userAgent());
        subscriptions.save(sub);
    }

    /** Forget the caller's subscription for {@code endpoint}. Idempotent. */
    @Transactional
    public void unsubscribe(String endpoint) {
        subscriptions.deleteByUserIdAndEndpoint(currentUserId(), endpoint);
    }

    /**
     * Queue delivery of freshly persisted notifications. Call inside the
     * persisting transaction: the payload is snapshotted now, sending
     * starts after commit (never for a rolled-back row).
     */
    public void dispatch(List<Notification> rows) {
        if (client == null || rows == null || rows.isEmpty()) return;
        List<PushMessage> messages = rows.stream().map(this::toMessage).toList();
        Tx.afterCommit(() -> executor.execute(() -> deliver(messages)));
    }

    private void deliver(List<PushMessage> messages) {
        try {
            Map<Long, List<PushSubscription>> byUser = subscriptions
                    .findAllByUserIdIn(messages.stream().map(PushMessage::userId).collect(Collectors.toSet()))
                    .stream()
                    .collect(Collectors.groupingBy(PushSubscription::getUserId));
            for (PushMessage m : messages) {
                for (PushSubscription sub : byUser.getOrDefault(m.userId(), List.of())) {
                    inFlight.acquire();
                    executor.execute(() -> {
                        try {
                            sendOne(sub, m);
                        } finally {
                            inFlight.release();
                        }
                    });
                }
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        } catch (RuntimeException e) {
            log.warn("Web Push fan-out failed: {}", e.getMessage());
        }
    }

    private void sendOne(PushSubscription sub, PushMessage m) {
        try {
            int status = client.send(sub.getEndpoint(), sub.getP256dh(), sub.getAuth(), m.payload(), m.urgent());
            if (status == 404 || status == 410) {
                subscriptions.deleteByEndpoint(sub.getEndpoint());
                log.debug("Dropped expired push subscription {} of user {}", sub.getId(), sub.getUserId());
            } else if (status >= 400) {
                log.warn("Push service answered {} for subscription {} of user {}", status, sub.getId(), sub.getUserId());
            }
        } catch (IOException e) {
            log.warn("Push to subscription {} failed: {}", sub.getId(), e.getMessage());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        } catch (RuntimeException e) {
            log.warn("Push to subscription {} failed: {}", sub.getId(), e.getMessage());
        }
    }

    /** The JSON the service worker renders — mirrors the SSE envelope. */
    private PushMessage toMessage(Notification n) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("id", n.getId());
        body.put("type", n.getType());
        body.put("title", n.getTitle());
        body.put("body", truncate(n.getBody()));
        body.put("link", n.getLink());
        if (n.getCategory() != null) body.put("category", n.getCategory().name());
        body.put("priority", n.getPriority().name());
        body.put("createdAt", n.getCreatedAt() == null ? null : n.getCreatedAt().toString());
        return new PushMessage(n.getUserId(), json.writeValueAsBytes(body),
                n.getPriority() == NotificationPriority.HIGH);
    }

    private static String truncate(String s) {
        if (s == null || s.length() <= BODY_MAX_CHARS) return s;
        return s.substring(0, BODY_MAX_CHARS - 1) + "…";
    }

    private static boolean isValidAuth(String auth) {
        try {
            return WebPushCrypto.b64(auth).length == 16;
        } catch (IllegalArgumentException e) {
            return false;
        }
    }

    private static long currentUserId() {
        return CurrentUserHolder.get()
                .map(u -> u.userId())
                .orElseThrow(() -> new UnauthorizedError(
                        CommonErrorCode.UNAUTHENTICATED, "no current user"));
    }
}
