package com.forward.hrms.chat.service;

import com.forward.hrms.chat.dto.response.PresenceDto;
import com.forward.hrms.chat.repository.ConversationMemberRepository;
import com.forward.hrms.commons.core.context.CurrentUserHolder;
import com.forward.hrms.commons.core.error.CommonErrorCode;
import com.forward.hrms.commons.core.error.UnauthorizedError;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * Read side of presence. Writes are owned by the BFF (it has the SSE
 * lifecycle); we just read the Redis keys it maintains.
 *
 * <p>Keys:
 * <ul>
 *   <li>{@code presence:{userId}} — short-TTL "i'm here right now" key,
 *       refreshed by BFF on every SSE heartbeat. If present → online.</li>
 *   <li>{@code presence:lastseen:{userId}} — no TTL; written on every
 *       heartbeat. Used to render "last seen 5 min ago" when offline.</li>
 * </ul>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PresenceService {

    public static final String ONLINE_KEY_PREFIX = "presence:";
    public static final String LASTSEEN_KEY_PREFIX = "presence:lastseen:";

    private final StringRedisTemplate redis;
    private final ConversationMemberRepository members;

    public List<PresenceDto> snapshot(List<Long> userIds) {
        if (userIds == null || userIds.isEmpty()) return List.of();

        long callerId = CurrentUserHolder.get()
                .map(u -> u.userId())
                .orElseThrow(() -> new UnauthorizedError(CommonErrorCode.UNAUTHENTICATED, "no current user"));

        // §7.chat #21 — only return rows for users the caller shares a
        // conversation with (plus themselves). Hidden ids are silently
        // dropped so a presence GET can't be used to enumerate org user
        // ids.
        Set<Long> candidates = new LinkedHashSet<>();
        for (Long uid : userIds) {
            if (uid != null && uid > 0) candidates.add(uid);
        }
        if (candidates.isEmpty()) return List.of();

        Set<Long> visible = new HashSet<>();
        visible.add(callerId);
        if (candidates.size() > 1 || !candidates.contains(callerId)) {
            visible.addAll(members.findVisiblePeerIds(callerId, candidates));
        }

        List<PresenceDto> out = new ArrayList<>(candidates.size());
        for (Long uid : userIds) {
            if (uid == null || uid <= 0) continue;
            if (!visible.contains(uid)) continue;
            String online = redis.opsForValue().get(ONLINE_KEY_PREFIX + uid);
            String lastSeen = redis.opsForValue().get(LASTSEEN_KEY_PREFIX + uid);
            LocalDateTime ls = parseLastSeen(uid, lastSeen);
            out.add(new PresenceDto(uid, online != null ? "online" : "offline", ls));
        }
        return out;
    }

    private static LocalDateTime parseLastSeen(long userId, String s) {
        if (s == null || s.isBlank()) return null;
        try {
            return LocalDateTime.parse(s);
        } catch (Exception e) {
            log.debug("presence: ignoring unparseable lastseen key for user {} (value={})", userId, s);
            return null;
        }
    }
}
