package com.forward.hrms.auth.service;

import com.forward.hrms.auth.dto.response.SessionDto;
import com.forward.hrms.auth.dto.response.SessionDtoMapper;
import com.forward.hrms.auth.entity.Session;
import com.forward.hrms.auth.entity.User;
import com.forward.hrms.auth.enums.AuthErrorCode;
import com.forward.hrms.auth.enums.RevokeReason;
import com.forward.hrms.auth.events.AuthEvents;
import com.forward.hrms.auth.jwt.AuthJwtProperties;
import com.forward.hrms.auth.repository.SessionRepository;
import com.forward.hrms.commons.core.context.RequestContext;
import com.forward.hrms.commons.core.error.ForbiddenError;
import com.forward.hrms.commons.core.error.NotFoundError;
import com.forward.hrms.commons.core.tx.Tx;
import com.forward.hrms.commons.kafka.outbox.AuditEmitter;
import com.forward.hrms.commons.kafka.outbox.Outbox;

import lombok.RequiredArgsConstructor;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Base64;
import java.util.List;
import java.util.Map;

/**
 * Single source of truth for the session row + its Redis refresh-token mirror.
 * Every create / rotate / revoke runs through here so the DB row, the
 * {@code auth:refresh:{id}} Redis entry, the {@code auth.events} stream, and the
 * audit log never drift. Login / refresh / change-password own only the
 * credential and JWT concerns; they delegate the session mechanics to this class.
 */
@Service
@RequiredArgsConstructor
public class SessionService {

    /** How long a revoked session is kept visible in the user's own list. */
    private static final Duration REVOKED_TAIL = Duration.ofDays(7);

    private static final SecureRandom RAND = new SecureRandom();

    private final SessionRepository sessions;
    private final RefreshTokenStore refreshStore;
    private final AuthJwtProperties jwtProps;
    private final Outbox outbox;
    private final AuditEmitter audit;

    /**
     * Persist a session for a freshly-authenticated user, mirror its refresh
     * token into Redis after commit, and emit {@code SESSION_CREATED}. Runs in
     * the caller's login transaction (same commit boundary as the User row
     * update) — hence {@code MANDATORY}.
     */
    @Transactional(propagation = Propagation.MANDATORY)
    public Created create(User user, LocalDateTime now) {
        RequestContext ctx = RequestContext.current().orElse(null);
        String refreshTokenId = randomToken();

        Session s = new Session();
        s.setUserId(user.getId());
        s.setRefreshTokenId(refreshTokenId);
        s.setIssuedAt(now);
        s.setLastSeenAt(now);
        s.setExpiresAt(now.plus(jwtProps.refreshTokenTtl()));
        if (ctx != null) {
            s.setIpAddress(ctx.ipAddress());
            s.setUserAgent(ctx.userAgent());
            s.setDeviceLabel(UserAgentParser.describe(ctx.userAgent()));
        } else {
            s.setDeviceLabel("Unknown device");
        }
        sessions.save(s);

        long sid = s.getId();
        long uid = user.getId();
        Tx.afterCommit(() -> refreshStore.put(refreshTokenId, uid, sid, jwtProps.refreshTokenTtl()));
        outbox.publish(AuthEvents.TOPIC, "user:" + uid, AuthEvents.SESSION_CREATED,
                new AuthEvents.SessionCreated(sid, uid, s.getIpAddress(), s.getUserAgent()));
        return new Created(s, refreshTokenId);
    }

    /**
     * Rotate a session's refresh token: mint a new opaque id, bump
     * {@code lastSeenAt} + {@code expiresAt}, and swap the Redis entry after
     * commit. Shared by {@code /auth/refresh} and change-password. Returns the
     * new refresh token id. {@code MANDATORY} — always part of the caller's
     * business transaction.
     */
    @Transactional(propagation = Propagation.MANDATORY)
    public String rotate(Session session, LocalDateTime now) {
        String oldRefreshTokenId = session.getRefreshTokenId();
        String newRefresh = randomToken();
        session.setRefreshTokenId(newRefresh);
        session.setLastSeenAt(now);
        session.setExpiresAt(now.plus(jwtProps.refreshTokenTtl()));

        long uid = session.getUserId();
        long sid = session.getId();
        Tx.afterCommit(() -> {
            refreshStore.delete(oldRefreshTokenId);
            refreshStore.put(newRefresh, uid, sid, jwtProps.refreshTokenTtl());
        });
        return newRefresh;
    }

    /**
     * End the session backing a refresh token (logout). Idempotent: an unknown
     * or already-revoked token clears Redis and returns. Routes through the
     * canonical {@link #revoke} so logout emits the same event + audit row as
     * any other revocation.
     */
    @Transactional
    public void logoutByRefreshToken(String refreshTokenId, String byUsername) {
        var lookup = refreshStore.get(refreshTokenId).orElse(null);
        Tx.afterCommit(() -> refreshStore.delete(refreshTokenId));
        if (lookup == null) return;
        sessions.findById(lookup.sessionId())
                .ifPresent(s -> revoke(s, RevokeReason.USER_LOGOUT, byUsername));
    }

    @Transactional(readOnly = true)
    public List<SessionDto> listOwn(long userId, long currentSessionId) {
        return sessions.findAllActiveOrRecentlyRevoked(userId, LocalDateTime.now().minus(REVOKED_TAIL)).stream()
                .map(s -> SessionDtoMapper.of(s, currentSessionId))
                .toList();
    }

    @Transactional
    public SessionDto revokeOwn(long userId, long sessionId, long currentSessionId, String byUsername) {
        Session s = ownSession(userId, sessionId);
        revoke(s, RevokeReason.USER_REVOKE, byUsername);
        return SessionDtoMapper.of(s, currentSessionId);
    }

    @Transactional
    public List<SessionDto> revokeAllOwnExceptCurrent(long userId, long currentSessionId, String byUsername) {
        List<SessionDto> killed = new java.util.ArrayList<>();
        for (Session s : sessions.findAllByUserIdAndRevokedAtIsNullOrderByLastSeenAtDesc(userId)) {
            if (s.getId() == currentSessionId) continue;
            revoke(s, RevokeReason.USER_REVOKE, byUsername);
            killed.add(SessionDtoMapper.of(s, currentSessionId));
        }
        return killed;
    }

    @Transactional
    public SessionDto revokeAsAdmin(long sessionId, String byUsername) {
        Session s = sessions.findById(sessionId)
                .orElseThrow(() -> new NotFoundError(AuthErrorCode.SESSION_NOT_FOUND, "session not found"));
        revoke(s, RevokeReason.ADMIN_REVOKE, byUsername);
        return SessionDtoMapper.of(s, 0L);
    }

    @Transactional(readOnly = true)
    public List<SessionDto> listForUserAsAdmin(long userId) {
        return sessions.findAllByUserIdAndRevokedAtIsNullOrderByLastSeenAtDesc(userId).stream()
                .map(s -> SessionDtoMapper.of(s, 0L))
                .toList();
    }

    private Session ownSession(long userId, long sessionId) {
        Session s = sessions.findById(sessionId)
                .orElseThrow(() -> new NotFoundError(AuthErrorCode.SESSION_NOT_FOUND, "session not found"));
        if (s.getUserId() != userId) {
            throw new ForbiddenError("session does not belong to caller");
        }
        return s;
    }

    private void revoke(Session s, RevokeReason reason, String byUsername) {
        if (s.getRevokedAt() != null) return;
        s.setRevokedAt(LocalDateTime.now());
        s.setRevokedBy(byUsername);
        s.setRevokedReason(reason);
        String refreshTokenId = s.getRefreshTokenId();
        Tx.afterCommit(() -> refreshStore.delete(refreshTokenId));
        outbox.publish(AuthEvents.TOPIC, "user:" + s.getUserId(), AuthEvents.SESSION_REVOKED,
                new AuthEvents.SessionRevoked(s.getId(), s.getUserId(), reason.name(), byUsername));
        audit.emit("session", String.valueOf(s.getId()), "revoked",
                null, Map.of("reason", reason.name(), "by", byUsername),
                reason == RevokeReason.USER_REVOKE || reason == RevokeReason.USER_LOGOUT);
    }

    private static String randomToken() {
        byte[] buf = new byte[32];
        RAND.nextBytes(buf);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(buf);
    }

    /** A freshly-created session plus the plaintext refresh token id to hand back. */
    public record Created(Session session, String refreshTokenId) {}
}
