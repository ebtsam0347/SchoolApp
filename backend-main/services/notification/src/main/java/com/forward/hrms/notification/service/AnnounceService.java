package com.forward.hrms.notification.service;

import com.forward.hrms.commons.batch.BatchOutcomes;
import com.forward.hrms.commons.batch.BatchRunner;
import com.forward.hrms.commons.core.error.UnprocessableError;
import com.forward.hrms.commons.kafka.outbox.AuditEmitter;
import com.forward.hrms.notification.config.NotificationProperties;
import com.forward.hrms.notification.dto.request.AnnounceRequest;
import com.forward.hrms.notification.dto.response.AnnounceResponse;
import com.forward.hrms.notification.enums.NotificationCategory;
import com.forward.hrms.notification.enums.NotificationErrorCode;
import com.forward.hrms.notification.enums.NotificationPriority;
import com.forward.hrms.notification.enums.NotificationType;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Admin broadcast — fans out one row per recipient. The notification
 * service does not own the "all employees in dept X" lookup; the caller
 * (typically the BFF) resolves the recipient list from employee-service
 * and passes user ids in.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AnnounceService {

    private final NotificationService notifications;
    private final NotificationProperties props;
    private final BatchRunner batch;
    private final AuditEmitter audit;
    private final TransactionTemplate tx;

    /**
     * §2.12 — per-recipient partial via {@link BatchRunner}. Each
     * dispatch runs in its own {@code REQUIRES_NEW} transaction; one
     * bad row (unknown user, opt-out lookup blip, template render
     * failure) leaves the rest of the announcement delivered. Used to
     * be a single tx that rolled back the entire fan-out on the first
     * failure.
     */
    public AnnounceResponse announce(AnnounceRequest req) {
        Set<Long> recipients = dedupe(req.userIds());
        if (recipients.isEmpty()) {
            throw new UnprocessableError(
                    NotificationErrorCode.BROADCAST_NO_RECIPIENTS, "no recipients resolved");
        }
        if (recipients.size() > props.broadcastMaxRecipients()) {
            throw new UnprocessableError(
                    NotificationErrorCode.BROADCAST_RECIPIENT_LIMIT_EXCEEDED,
                    "recipient list exceeds max " + props.broadcastMaxRecipients());
        }
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("title", req.title());
        if (req.body() != null) payload.put("body", req.body());
        if (req.link() != null) payload.put("link", req.link());

        // Admin audit row first, in its own short tx — the fan-out runs
        // outside any outer tx so a 5k-recipient broadcast doesn't hold a
        // DB connection for the whole duration.
        recordBroadcast(req, recipients.size());

        BatchOutcomes<Long, Void> outcomes = batch.runWithPartialVoid(recipients, userId ->
                notifications.dispatchAd(
                        userId,
                        NotificationType.ANNOUNCEMENT,
                        req.title(),
                        req.body(),
                        req.link(),
                        NotificationCategory.ANNOUNCEMENT,
                        req.priority() == null ? NotificationPriority.NORMAL : req.priority(),
                        payload));
        for (var f : outcomes.failed()) {
            log.warn("announce: user {} failed code={} msg={}",
                    f.item(), f.errorCode(), f.message());
        }
        return new AnnounceResponse(outcomes.succeeded().size());
    }

    /**
     * Bracketed in its own short transaction via {@link TransactionTemplate}
     * — Outbox.publish is {@code Propagation.MANDATORY}, but we don't
     * want a long-running outer tx wrapping the per-recipient fan-out.
     */
    private void recordBroadcast(AnnounceRequest req, int recipientCount) {
        tx.executeWithoutResult(status -> {
            Map<String, Object> auditPayload = new LinkedHashMap<>();
            auditPayload.put("title", req.title());
            auditPayload.put("recipientCount", recipientCount);
            auditPayload.put("priority", req.priority() == null
                    ? NotificationPriority.NORMAL.name() : req.priority().name());
            audit.emit("notification_broadcast", UUID.randomUUID().toString(), "announced",
                    null, auditPayload, false);
        });
    }

    private static Set<Long> dedupe(java.util.List<Long> ids) {
        Set<Long> out = new HashSet<>();
        if (ids == null) return out;
        for (Long id : ids) if (id != null && id > 0) out.add(id);
        return out;
    }
}
