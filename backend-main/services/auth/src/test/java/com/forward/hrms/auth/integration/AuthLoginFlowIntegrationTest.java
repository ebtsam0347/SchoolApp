package com.forward.hrms.auth.integration;

import com.forward.hrms.auth.dto.request.LoginRequest;
import com.forward.hrms.auth.dto.response.TokenResponse;
import com.forward.hrms.auth.entity.Session;
import com.forward.hrms.auth.entity.User;
import com.forward.hrms.auth.enums.RevokeReason;
import com.forward.hrms.auth.enums.UserStatus;
import com.forward.hrms.auth.repository.SessionRepository;
import com.forward.hrms.auth.repository.UserRepository;
import com.forward.hrms.auth.service.LoginService;
import com.forward.hrms.auth.service.RefreshTokenStore;
import com.forward.hrms.commons.testsupport.containers.WithTestcontainers;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.crypto.password.PasswordEncoder;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * End-to-end auth flow against real Postgres + Redis + Kafka. Hits the
 * three FE-critical endpoints: login → refresh → logout. Asserts the
 * full contract:
 *
 * <ul>
 *   <li>JWT issued, refresh + session row + Redis lookup all created together</li>
 *   <li>Refresh rotates the token id; old token gone, new token live</li>
 *   <li>Logout revokes the session and deletes the Redis lookup; idempotent</li>
 * </ul>
 *
 * <p>Seeds its own test user instead of leaning on the bootstrap admin —
 * Testcontainers reuses the DB across runs and {@code AdminBootstrap}
 * skips re-creation if the row exists, so a stale hash from a prior
 * bootstrap config breaks the test.
 */
@SpringBootTest
@WithTestcontainers
class AuthLoginFlowIntegrationTest {

    private static final String USERNAME = "login-test-user";
    private static final String PASSWORD = "TestPass!2026";

    @Autowired LoginService loginService;
    @Autowired UserRepository users;
    @Autowired SessionRepository sessions;
    @Autowired RefreshTokenStore refreshStore;
    @Autowired PasswordEncoder passwordEncoder;

    /**
     * Seed a known user for each test so we don't depend on the bootstrap
     * admin (Testcontainers reuses the DB across runs, and the AdminBootstrap
     * skips re-creation if the row exists — leaving stale password hashes
     * from prior bootstrap configs).
     */
    @BeforeEach
    void seedUser() {
        sessions.deleteAll();
        users.findByUsername(USERNAME).ifPresent(users::delete);
        users.flush();

        User u = new User();
        u.setUsername(USERNAME);
        u.setFullName("Login Test User");
        u.setPasswordHash(passwordEncoder.encode(PASSWORD));
        u.setStatus(UserStatus.ACTIVE);
        u.setMustChangePw(true);
        u.setEmployeeLinked(false);
        users.save(u);
    }

    @Test
    void login_persistsSession_writesRefreshToRedis_returnsAccessToken() {
        TokenResponse tokens = loginService.login(new LoginRequest(USERNAME, PASSWORD));

        assertThat(tokens.accessToken()).isNotBlank();
        assertThat(tokens.refreshToken()).isNotBlank();
        assertThat(tokens.sessionId()).isPositive();
        assertThat(tokens.accessTokenExpiresAt()).isNotNull();
        assertThat(tokens.refreshTokenExpiresAt()).isNotNull();
        // Bootstrap admin always has must_change_pw=true.
        assertThat(tokens.mustChangePassword()).isTrue();

        Session s = sessions.findById(tokens.sessionId()).orElseThrow();
        assertThat(s.getRefreshTokenId()).isEqualTo(tokens.refreshToken());
        assertThat(s.getRevokedAt()).isNull();
        assertThat(s.isActive()).isTrue();

        var lookup = refreshStore.get(tokens.refreshToken()).orElseThrow();
        assertThat(lookup.sessionId()).isEqualTo(tokens.sessionId());
        assertThat(lookup.userId()).isEqualTo(s.getUserId());

        // failed_logins reset, last_login_at stamped
        User u = users.findById(s.getUserId()).orElseThrow();
        assertThat(u.getFailedLogins()).isZero();
        assertThat(u.getLockedUntil()).isNull();
        assertThat(u.getLastLoginAt()).isNotNull();
    }

    @Test
    void refresh_rotatesTheRefreshToken_invalidatesTheOld() {
        TokenResponse first = loginService.login(new LoginRequest(USERNAME, PASSWORD));

        TokenResponse rotated = loginService.refresh(first.refreshToken());

        assertThat(rotated.refreshToken()).isNotEqualTo(first.refreshToken());
        assertThat(rotated.sessionId()).isEqualTo(first.sessionId());
        assertThat(rotated.accessToken()).isNotBlank();

        // Old token cleared, new token live in Redis.
        assertThat(refreshStore.get(first.refreshToken())).isEmpty();
        assertThat(refreshStore.get(rotated.refreshToken())).isPresent();

        // Same session row, refresh_token_id rotated.
        Session s = sessions.findById(first.sessionId()).orElseThrow();
        assertThat(s.getRefreshTokenId()).isEqualTo(rotated.refreshToken());
        assertThat(s.getRevokedAt()).isNull();
    }

    @Test
    void refresh_withUnknownToken_throwsUnauthorized() {
        assertThatThrownBy(() -> loginService.refresh("not-a-real-token"))
                .isInstanceOf(com.forward.hrms.commons.core.error.UnauthorizedError.class);
    }

    @Test
    void logout_revokesSession_deletesRefresh_isIdempotent() {
        TokenResponse tokens = loginService.login(new LoginRequest(USERNAME, PASSWORD));

        loginService.logout(tokens.refreshToken(), USERNAME);

        Session s = sessions.findById(tokens.sessionId()).orElseThrow();
        assertThat(s.getRevokedAt()).isNotNull();
        assertThat(s.getRevokedBy()).isEqualTo(USERNAME);
        assertThat(s.getRevokedReason()).isEqualTo(RevokeReason.USER_LOGOUT);
        assertThat(refreshStore.get(tokens.refreshToken())).isEmpty();
        assertThat(s.isActive()).isFalse();

        // Idempotent — second call swallows quietly.
        loginService.logout(tokens.refreshToken(), USERNAME);
        Session reloaded = sessions.findById(tokens.sessionId()).orElseThrow();
        assertThat(reloaded.getRevokedAt()).isEqualTo(s.getRevokedAt());
    }

    @Test
    void login_withWrongPassword_throwsUnauthorized() {
        assertThatThrownBy(() -> loginService.login(new LoginRequest(USERNAME, "wrong")))
                .isInstanceOf(com.forward.hrms.commons.core.error.UnauthorizedError.class);

        // Failure recorder runs in REQUIRES_NEW; counter increments visibly.
        User u = users.findByUsername(USERNAME).orElseThrow();
        assertThat(u.getFailedLogins()).isPositive();
    }

    @Test
    void login_withUnknownUser_throwsUnauthorized() {
        assertThatThrownBy(() -> loginService.login(new LoginRequest("does-not-exist", "x")))
                .isInstanceOf(com.forward.hrms.commons.core.error.UnauthorizedError.class);
    }
}
