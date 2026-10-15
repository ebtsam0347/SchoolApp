package com.forward.hrms.auth.service;

import java.time.LocalDateTime;
import java.util.Map;

import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.forward.hrms.auth.dto.request.PasswordChangeRequest;
import com.forward.hrms.auth.dto.response.TokenResponse;
import com.forward.hrms.auth.entity.Session;
import com.forward.hrms.auth.entity.User;
import com.forward.hrms.auth.enums.AuthErrorCode;
import com.forward.hrms.auth.enums.RevokeReason;
import com.forward.hrms.auth.events.AuthEvents;
import com.forward.hrms.auth.jwt.JwtIssuer;
import com.forward.hrms.auth.repository.SessionRepository;
import com.forward.hrms.auth.repository.UserRepository;
import com.forward.hrms.commons.core.context.CurrentUser;
import com.forward.hrms.commons.core.context.CurrentUserHolder;
import com.forward.hrms.commons.core.error.NotFoundError;
import com.forward.hrms.commons.core.error.UnauthorizedError;
import com.forward.hrms.commons.core.time.InstantBridge;
import com.forward.hrms.commons.kafka.outbox.AuditEmitter;
import com.forward.hrms.commons.kafka.outbox.Outbox;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class PasswordService {

    private final UserRepository users;
    private final SessionRepository sessions;
    private final PasswordEncoder encoder;
    private final JwtIssuer issuer;
    private final SessionService sessionService;
    private final Outbox outbox;
    private final AuditEmitter audit;

    /**
     * Change the caller's password and rotate the active session. Returns
     * a fresh {@link TokenResponse} so the FE doesn't have to chase a
     * separate /auth/refresh — without that, the access token in the
     * caller's hand still carries {@code pwChangeRequired: true} until
     * they refresh, which the change-password screen can't easily do.
     * §B15.
     */
    @Transactional
    public TokenResponse changeOwnPassword(PasswordChangeRequest req) {
        CurrentUser cu = CurrentUserHolder.get()
                .orElseThrow(() -> new UnauthorizedError("not authenticated"));
        if (cu.sessionId() == 0L) {
            // Service-account JWTs (sid=0) have no session row to rotate.
            // They also don't go through change-password, but be explicit.
            throw new UnauthorizedError(AuthErrorCode.REFRESH_INVALID,
                    "service tokens cannot change passwords");
        }
        User user = users.findById(cu.userId())
                .orElseThrow(() -> new NotFoundError(AuthErrorCode.USER_NOT_FOUND, "user not found"));

        if (!encoder.matches(req.oldPassword(), user.getPasswordHash())) {
            throw new UnauthorizedError(AuthErrorCode.PASSWORD_MISMATCH, "old password mismatch");
        }
        PasswordPolicy.validate(req.newPassword(), user.getUsername());

        user.setPasswordHash(encoder.encode(req.newPassword()));
        user.setMustChangePw(false);

        Session current = sessions.findById(cu.sessionId())
                .orElseThrow(() -> new UnauthorizedError(AuthErrorCode.REFRESH_INVALID,
                        "session not found"));
        if (!current.isActive()) {
            throw new UnauthorizedError(AuthErrorCode.REFRESH_INVALID, "session inactive");
        }
        LocalDateTime now = LocalDateTime.now();
        sessions.revokeAllForUser(user.getId(), now, cu.username(),
                RevokeReason.PASSWORD_CHANGED, current.getId());

        // Rotate the current session's refresh token: the change-password
        // path commonly fires from a forced screen where the FE replaces
        // both tokens in one shot.
        String newRefresh = sessionService.rotate(current, now);

        JwtIssuer.Issued issued = issuer.issueAccessToken(user, current.getId());

        outbox.publish(AuthEvents.TOPIC, "user:" + user.getId(),
                AuthEvents.USER_PASSWORD_CHANGED,
                new AuthEvents.UserPasswordChanged(user.getId()));
        audit.emit("user", String.valueOf(user.getId()), "password.changed",
                null, Map.of("by", cu.username()), true,
                "Password changed");

        return new TokenResponse(
                issued.token(),
                InstantBridge.toLocal(issued.expiresAt()),
                newRefresh,
                current.getExpiresAt(),
                current.getId(),
                user.isMustChangePw());
    }
}
