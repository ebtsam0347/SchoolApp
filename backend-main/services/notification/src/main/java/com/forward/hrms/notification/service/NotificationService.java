package com.forward.hrms.notification.service;

import com.forward.hrms.commons.core.context.CurrentUserHolder;
import com.forward.hrms.commons.core.error.CommonErrorCode;
import com.forward.hrms.commons.core.error.NotFoundError;
import com.forward.hrms.commons.core.error.UnauthorizedError;
import com.forward.hrms.commons.web.page.PageResponse;
import com.forward.hrms.commons.web.page.PageableDefaults;
import com.forward.hrms.notification.dto.response.NotificationDto;
import com.forward.hrms.notification.dto.response.NotificationTypeDto;
import com.forward.hrms.notification.dto.response.UnreadCountResponse;
import com.forward.hrms.notification.entity.Notification;
import com.forward.hrms.notification.entity.NotificationPreference;
import com.forward.hrms.notification.entity.NotificationTemplate;
import com.forward.hrms.notification.enums.NotificationCategory;
import com.forward.hrms.notification.enums.NotificationErrorCode;
import com.forward.hrms.notification.enums.NotificationPriority;
import com.forward.hrms.notification.repository.NotificationPreferenceRepository;
import com.forward.hrms.notification.repository.NotificationRepository;
import com.forward.hrms.notification.repository.NotificationSpecifications;
import com.forward.hrms.notification.repository.NotificationTemplateRepository;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import tools.jackson.databind.ObjectMapper;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Core notification ops — read/insert/mark-read — plus the
 * {@link #dispatch(long, String, Map)} entry point Kafka consumers and
 * the broadcast endpoint share. Templates are looked up at dispatch time
 * (cheap) so a template edit takes effect for every subsequent
 * notification without a service restart.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class NotificationService {

    private final NotificationRepository notifications;
    private final NotificationTemplateRepository templates;
    private final NotificationPreferenceRepository preferences;
    private final NotificationPublisher publisher;
    private final TemplateRenderer renderer;
    private final ObjectMapper json;

    // ----- bell feed reads -----

    @Transactional(readOnly = true)
    public PageResponse<NotificationDto> list(boolean unreadOnly,
                                              NotificationCategory category,
                                              Pageable pageable) {
        long userId = currentUserId();
        Pageable p = PageableDefaults.apply(pageable, "createdAt");
        return PageResponse.from(notifications
                .findAll(NotificationSpecifications.forBellFeed(userId, unreadOnly, category), p)
                .map(NotificationDto::of));
    }

    @Transactional(readOnly = true)
    public List<NotificationTypeDto> listTypes() {
        return templates.findAllByOrderByTypeAsc().stream()
                .filter(NotificationTemplate::isActive)
                .map(NotificationTypeDto::of)
                .toList();
    }

    @Transactional(readOnly = true)
    public UnreadCountResponse unreadCount() {
        long userId = currentUserId();
        Long cached = publisher.readUnread(userId);
        if (cached != null && cached >= 0) {
            return new UnreadCountResponse(cached);
        }
        long count = notifications.countByUserIdAndReadAtIsNull(userId);
        publisher.setUnread(userId, count);
        return new UnreadCountResponse(count);
    }

    // ----- mutations from controller -----

    @Transactional
    public NotificationDto markRead(long id) {
        long userId = currentUserId();
        Notification n = notifications.findById(id)
                .orElseThrow(() -> new NotFoundError(
                        NotificationErrorCode.NOTIFICATION_NOT_FOUND, "notification not found"));
        if (!n.getUserId().equals(userId)) {
            // Don't leak existence — same shape as not found.
            throw new NotFoundError(NotificationErrorCode.NOTIFICATION_NOT_FOUND, "notification not found");
        }
        if (n.getReadAt() == null) {
            n.setReadAt(LocalDateTime.now());
            publisher.decrementUnread(userId, 1);
        }
        return NotificationDto.of(n);
    }

    /**
     * Bulk mark-read. Returns the number of rows that actually transitioned
     * from unread → read (already-read ids are silently ignored, ids that
     * don't belong to the caller are filtered out by the WHERE clause).
     * The unread cache is decremented by the same number.
     */
    @Transactional
    public int markRead(java.util.Collection<Long> ids) {
        if (ids == null || ids.isEmpty()) return 0;
        long userId = currentUserId();
        int updated = notifications.markRead(userId, ids, LocalDateTime.now());
        if (updated > 0) {
            publisher.decrementUnread(userId, updated);
        }
        return updated;
    }

    @Transactional
    public int markAllRead() {
        long userId = currentUserId();
        int updated = notifications.markAllRead(userId, LocalDateTime.now());
        publisher.resetUnread(userId);
        return updated;
    }

    // ----- preferences -----

    @Transactional(readOnly = true)
    public List<NotificationPreference> myPreferences() {
        return preferences.findAllByUserId(currentUserId());
    }

    @Transactional
    @org.springframework.cache.annotation.CacheEvict(
            value = com.forward.hrms.notification.config.NotificationCacheConfig.CACHE_OPTOUTS,
            allEntries = true)
    public List<NotificationPreference> updatePreferences(Map<String, Boolean> patch) {
        long userId = currentUserId();
        if (patch == null || patch.isEmpty()) return preferences.findAllByUserId(userId);
        Map<String, NotificationPreference> existing = new HashMap<>();
        for (NotificationPreference p : preferences.findAllByUserId(userId)) {
            existing.put(p.getType(), p);
        }
        for (Map.Entry<String, Boolean> entry : patch.entrySet()) {
            if (entry.getValue() == null) continue;
            NotificationPreference p = existing.get(entry.getKey());
            if (p == null) {
                p = new NotificationPreference();
                p.setUserId(userId);
                p.setType(entry.getKey());
            }
            p.setEnabled(entry.getValue());
            preferences.save(p);
        }
        return preferences.findAllByUserId(userId);
    }

    // ----- dispatch from event consumers / broadcast -----

    /**
     * Render and persist one notification, bump the unread cache, and
     * publish to the live channel. Skipped (returns null) when the user
     * has opted out of this type or the template is missing/inactive.
     */
    @Transactional
    public Notification dispatch(long userId, String type, Map<String, Object> vars) {
        if (isOptedOut(userId, type)) {
            log.debug("User {} opted out of {} — skipping", userId, type);
            return null;
        }
        NotificationTemplate t = lookupTemplate(type);
        if (t == null) {
            log.warn("No active template for type {} — skipping dispatch to user {}", type, userId);
            return null;
        }
        Notification n = persist(userId, type, t, vars);
        publisher.publish(n);
        return n;
    }

    /**
     * Cached template lookup. {@code @Cacheable} on a self-call would
     * not fire — we proxy through {@code lookupTemplate} so the cache
     * advice can intercept. {@link TemplateService#update} evicts.
     */
    @org.springframework.cache.annotation.Cacheable(
            value = com.forward.hrms.notification.config.NotificationCacheConfig.CACHE_TEMPLATES,
            key = "#type",
            unless = "#result == null")
    public NotificationTemplate lookupTemplate(String type) {
        return templates.findByType(type).filter(NotificationTemplate::isActive).orElse(null);
    }

    /**
     * Bulk dispatch — one template lookup, one batch opt-out check, one
     * {@code saveAll} for the rows, and {@link NotificationPublisher#publishBulk}
     * for the live channel + bell increments. Replaces the per-recipient
     * loop in {@code EventDispatcher.onChatMessageSent} (a 50-person group
     * chat went from 50 × (template fetch + opt-out lookup + INSERT +
     * Redis SET + PUBLISH) to 1 × template fetch + 1 × opt-out batch +
     * 1 × INSERT batch + 1 × pipelined Redis fan-out).
     *
     * <p>Returns the persisted notifications (in caller-supplied
     * {@code userIds} order, opt-outs filtered out). Empty list on
     * missing template or every-recipient-opted-out.
     */
    @Transactional
    public List<Notification> dispatchBulk(java.util.Collection<Long> userIds, String type, Map<String, Object> vars) {
        if (userIds == null || userIds.isEmpty()) return List.of();
        NotificationTemplate t = lookupTemplate(type);
        if (t == null) {
            log.warn("No active template for type {} — skipping bulk dispatch to {} users", type, userIds.size());
            return List.of();
        }
        java.util.Set<Long> distinct = new java.util.LinkedHashSet<>(userIds);
        java.util.Set<Long> optedOut = new java.util.HashSet<>(preferences.findOptedOutUserIds(distinct, type));
        List<Notification> rows = new java.util.ArrayList<>(distinct.size());
        String title = renderer.render(t.getTitleTpl(), vars);
        String body  = renderer.render(t.getBodyTpl(),  vars);
        String link  = renderer.render(t.getLinkTpl(),  vars);
        String payload = (vars == null || vars.isEmpty()) ? null : toJson(vars);
        for (Long uid : distinct) {
            if (uid == null || optedOut.contains(uid)) continue;
            Notification n = new Notification();
            n.setUserId(uid);
            n.setType(type);
            n.setTitle(title);
            n.setBody(body);
            n.setLink(link);
            n.setCategory(t.getCategory());
            n.setPriority(NotificationPriority.NORMAL);
            n.setPayload(payload);
            rows.add(n);
        }
        if (rows.isEmpty()) return List.of();
        notifications.saveAll(rows);
        publisher.publishBulk(rows);
        return rows;
    }

    /**
     * Same as {@link #dispatch} but for a fixed (title, body, link) — used by
     * the admin broadcast endpoint where there is no template lookup.
     */
    @Transactional
    public Notification dispatchAd(long userId, String type, String title, String body, String link,
                                    NotificationCategory category, NotificationPriority priority,
                                    Map<String, Object> payloadFields) {
        Notification n = new Notification();
        n.setUserId(userId);
        n.setType(type);
        n.setTitle(title);
        n.setBody(body);
        n.setLink(link);
        n.setCategory(category);
        n.setPriority(priority == null ? NotificationPriority.NORMAL : priority);
        n.setPayload(payloadFields == null || payloadFields.isEmpty() ? null : toJson(payloadFields));
        notifications.save(n);
        publisher.publish(n);
        return n;
    }

    // ----- internals -----

    private Notification persist(long userId, String type, NotificationTemplate t, Map<String, Object> vars) {
        Notification n = new Notification();
        n.setUserId(userId);
        n.setType(type);
        n.setTitle(renderer.render(t.getTitleTpl(), vars));
        n.setBody(renderer.render(t.getBodyTpl(), vars));
        n.setLink(renderer.render(t.getLinkTpl(), vars));
        n.setCategory(t.getCategory());
        n.setPriority(NotificationPriority.NORMAL);
        n.setPayload(vars == null || vars.isEmpty() ? null : toJson(vars));
        notifications.save(n);
        return n;
    }

    /**
     * Cached opt-out check. The cache key is the {@code (userId, type)}
     * pair — invalidated on {@link #updatePreferences} (clears all to
     * keep it cheap; opt-out edits are rare). Single-recipient
     * {@link #dispatch} hits this; the bulk path uses the batch
     * repo lookup directly.
     */
    @org.springframework.cache.annotation.Cacheable(
            value = com.forward.hrms.notification.config.NotificationCacheConfig.CACHE_OPTOUTS,
            key = "T(java.util.Objects).hash(#userId, #type)")
    public boolean isOptedOut(long userId, String type) {
        return preferences.findById(new com.forward.hrms.notification.entity.NotificationPreferenceId(userId, type))
                .map(p -> !p.isEnabled())
                .orElse(false);
    }

    private String toJson(Map<String, Object> vars) {
        Map<String, Object> ordered = new LinkedHashMap<>(vars);
        try {
            return json.writeValueAsString(ordered);
        } catch (Exception e) {
            log.warn("Failed to serialize payload for type {}: {}", ordered.keySet(), e.getMessage());
            return null;
        }
    }

    private static long currentUserId() {
        return CurrentUserHolder.get()
                .map(u -> u.userId())
                .orElseThrow(() -> new UnauthorizedError(
                        CommonErrorCode.UNAUTHENTICATED, "no current user"));
    }
}
