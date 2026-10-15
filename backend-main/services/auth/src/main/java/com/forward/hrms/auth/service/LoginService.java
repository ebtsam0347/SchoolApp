package com.forward.hrms.auth.service;

import java.time.LocalDateTime;
import java.util.Map;
import java.util.Optional;

import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.forward.hrms.auth.dto.request.LoginRequest;
import com.forward.hrms.auth.dto.response.TokenResponse;
import com.forward.hrms.auth.entity.Session;
import com.forward.hrms.auth.entity.User;
import com.forward.hrms.auth.enums.AuthErrorCode;
import com.forward.hrms.auth.enums.UserStatus;
import com.forward.hrms.auth.jwt.JwtIssuer;
import com.forward.hrms.auth.repository.SessionRepository;
import com.forward.hrms.auth.repository.UserRepository;
import com.forward.hrms.commons.core.error.UnauthorizedError;
import com.forward.hrms.commons.core.time.InstantBridge;
import com.forward.hrms.commons.kafka.outbox.AuditEmitter;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
@RequiredArgsConstructor
public class LoginService {

    private static final String DUMMY_HASH = new BCryptPasswordEncoder(12).encode("login-timing-dummy");

    /** Cap the request-username key length so an attacker can't blow up Redis with megabyte usernames. */
    private static final int USERNAME_KEY_MAX = 64;

    private final UserRepository users;
    private final SessionRepository sessions;
    private final PasswordEncoder passwordEncoder;
    private final JwtIssuer issuer;
    private final RefreshTokenStore refreshStore;
    private final LoginFailureCounter failures;
    private final LoginFailureRecorder failureRecorder;
    private final SessionService sessionService;
    private final AuditEmitter audit;

    @Transactional
    public TokenResponse login(LoginRequest req) {
        Optional<User> opt = users.findByUsername(req.username());
        LocalDateTime now = LocalDateTime.now();

        if (opt.isEmpty()) {
            // §7.auth — burn a bcrypt against a dummy hash to keep timing
            // indistinguishable from the password-mismatch path. Increment
            // the counter on the raw (bounded) input so a spray across
            // unknown usernames is rate-limited too.
            passwordEncoder.matches(req.password(), DUMMY_HASH);
            failures.increment(boundedKey(req.username()));
            throw failOpaque(req.username(), "no user");
        }

        User user = opt.get();
        if (user.getStatus() == UserStatus.DISABLED) {
            audit.emitOnFailure("user", String.valueOf(user.getId()), "login.failed",
                    null, Map.of("reason", "USER_DISABLED"), false);
            throw new UnauthorizedError(AuthErrorCode.USER_DISABLED, "user disabled");
        }
        if (user.getLockedUntil() != null && user.getLockedUntil().isAfter(now)) {
            audit.emitOnFailure("user", String.valueOf(user.getId()), "login.failed",
                    null, Map.of("reason", "USER_LOCKED"), false);
            throw new UnauthorizedError(AuthErrorCode.USER_LOCKED, "user locked");
        }

        if (!passwordEncoder.matches(req.password(), user.getPasswordHash())) {
            failureRecorder.recordBadPassword(user.getId());
            throw failOpaque(req.username(), "bad password");
        }

        // Success path.
        failures.clear(user.getUsername());
        failures.clear(boundedKey(req.username()));
        user.setFailedLogins(0);
        user.setLockedUntil(null);
        user.setLastLoginAt(now);

        SessionService.Created created = sessionService.create(user, now);
        Session session = created.session();
        JwtIssuer.Issued issued = issuer.issueAccessToken(user, session.getId());

        audit.emit("user", String.valueOf(user.getId()), "login.success",
                null, Map.of("sessionId", session.getId()), true);

        return new TokenResponse(
                issued.token(),
                InstantBridge.toLocal(issued.expiresAt()),
                created.refreshTokenId(),
                session.getExpiresAt(),
                session.getId(),
                user.isMustChangePw());
    }

    private UnauthorizedError failOpaque(String username, String why) {
        log.debug("Login failed for {}: {}", username, why);
        return new UnauthorizedError(AuthErrorCode.INVALID_CREDENTIALS, "invalid credentials");
    }

    private static String boundedKey(String raw) {
        if (raw == null) return "";
        return raw.length() <= USERNAME_KEY_MAX ? raw : raw.substring(0, USERNAME_KEY_MAX);
    }

    @Transactional
    public void logout(String refreshTokenId, String byUsername) {
        sessionService.logoutByRefreshToken(refreshTokenId, byUsername);
    }

    @Transactional
    public TokenResponse refresh(String refreshTokenId) {
        var lookup = refreshStore.get(refreshTokenId)
                .orElseThrow(() -> new UnauthorizedError(AuthErrorCode.REFRESH_INVALID, "refresh token invalid"));
        Session session = sessions.findById(lookup.sessionId())
                .orElseThrow(() -> new UnauthorizedError(AuthErrorCode.REFRESH_INVALID, "refresh token invalid"));
        if (!session.isActive()) throw new UnauthorizedError(AuthErrorCode.REFRESH_INVALID, "session inactive");

        User user = users.findById(session.getUserId())
                .orElseThrow(() -> new UnauthorizedError(AuthErrorCode.REFRESH_INVALID, "user gone"));
        if (user.getStatus() != UserStatus.ACTIVE) {
            throw new UnauthorizedError(AuthErrorCode.USER_DISABLED, "user disabled");
        }

        String newRefresh = sessionService.rotate(session, LocalDateTime.now());
        JwtIssuer.Issued issued = issuer.issueAccessToken(user, session.getId());
        return new TokenResponse(
                issued.token(),
                InstantBridge.toLocal(issued.expiresAt()),
                newRefresh,
                session.getExpiresAt(),
                session.getId(),
                user.isMustChangePw());
    }
}
