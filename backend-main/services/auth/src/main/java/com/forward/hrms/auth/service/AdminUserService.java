package com.forward.hrms.auth.service;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.forward.hrms.auth.controller.AdminUserController;
import com.forward.hrms.auth.dto.request.CreateUserRequest;
import com.forward.hrms.auth.dto.request.UpdateUserRequest;
import com.forward.hrms.auth.dto.response.ServiceTokenResponse;
import com.forward.hrms.auth.dto.response.UserDto;
import com.forward.hrms.auth.dto.response.UserDtoMapper;
import com.forward.hrms.auth.dto.response.UserStatsResponse;
import com.forward.hrms.auth.entity.Role;
import com.forward.hrms.auth.entity.User;
import com.forward.hrms.auth.enums.AuthErrorCode;
import com.forward.hrms.auth.enums.RevokeReason;
import com.forward.hrms.auth.enums.UserStatus;
import com.forward.hrms.auth.events.AuthEvents;
import com.forward.hrms.auth.jwt.JwtIssuer;
import com.forward.hrms.auth.repository.RoleRepository;
import com.forward.hrms.auth.repository.SessionRepository;
import com.forward.hrms.auth.repository.UserRepository;
import com.forward.hrms.auth.repository.UserSearchSpecifications;
import com.forward.hrms.commons.core.context.CurrentUserHolder;
import com.forward.hrms.commons.core.error.ConflictError;
import com.forward.hrms.commons.core.error.NotFoundError;
import com.forward.hrms.commons.kafka.outbox.AuditEmitter;
import com.forward.hrms.commons.kafka.outbox.Outbox;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class AdminUserService {

    private final UserRepository users;
    private final RoleRepository roles;
    private final SessionRepository sessions;
    private final PasswordEncoder encoder;
    private final Outbox outbox;
    private final AuditEmitter audit;
    private final JwtIssuer jwtIssuer;

    @Transactional
    public UserDto create(CreateUserRequest req) {
        return create(req, false);
    }

    /**
     * @param idempotent when true (set by {@link AdminUserController} on a
     *                   request that carries an {@code Idempotency-Key}),
     *                   re-issuing the same {@code (username, employeeLinked)}
     *                   payload returns the existing user as 200 instead of
     *                   throwing 409. The saga retry path uses this so a
     *                   lost response on the first attempt doesn't blow up
     *                   the second.
     */
    @Transactional
    public UserDto create(CreateUserRequest req, boolean idempotent) {
        Optional<User> existingByUsername = users.findByUsername(req.username());
        if (existingByUsername.isPresent()) {
            User existing = existingByUsername.get();
            if (idempotent && existing.isEmployeeLinked() && req.employeeLinked()) {
                return UserDtoMapper.of(existing);
            }
            throw new ConflictError(AuthErrorCode.USERNAME_TAKEN, "username already in use");
        }
        String email = normaliseEmail(req.email());
        if (email != null && users.existsByEmailIgnoreCase(email)) {
            throw new ConflictError(AuthErrorCode.EMAIL_TAKEN, "email already in use");
        }
        PasswordPolicy.validate(req.temporaryPassword());

        User u = new User();
        u.setUsername(req.username());
        u.setFullName(req.fullName());
        u.setEmail(email);
        u.setPasswordHash(encoder.encode(req.temporaryPassword()));
        u.setStatus(UserStatus.ACTIVE);
        u.setMustChangePw(true);
        u.setEmployeeLinked(req.employeeLinked());
        u.setRoles(resolveRoles(req.roles()));
        users.save(u);

        outbox.publish(AuthEvents.TOPIC, "user:" + u.getId(), AuthEvents.USER_CREATED,
                new AuthEvents.UserCreated(u.getId(), u.getUsername(), u.getFullName(),
                        u.getEmail(), LocalDateTime.now()));
        audit.emit("user", String.valueOf(u.getId()), "created",
                null, snapshot(u), true);
        return UserDtoMapper.of(u);
    }

    /**
     * Idempotent: disabling an already-DISABLED user is a no-op. Used by
     * the termination saga's {@code DISABLE_AUTH_USER} step to cut the
     * Kafka-only path's tail risk (event sitting in outbox during an
     * auth outage with nobody watching the saga's status), and by the
     * admin UI "disable user" action.
     *
     * @param reason flows into the session-revoke {@link RevokeReason} and
     *               the {@code auth.user.deactivated} outbox event. Pass
     *               {@link RevokeReason#EMPLOYEE_TERMINATED} from the
     *               termination saga; {@link RevokeReason#ADMIN_REVOKE}
     *               for an admin-initiated disable.
     */
    @Transactional
    public UserDto disable(long userId, RevokeReason reason) {
        User u = loadOrThrow(userId);
        if (u.getStatus() == UserStatus.DISABLED) return UserDtoMapper.of(u);
        // §7.auth — last-ADMIN guard. Disabling the only remaining ACTIVE
        // ADMIN locks HR out of the system. Refuse with 409 instead of
        // silently breaking the install.
        guardLastAdmin(u);
        Map<String, Object> before = snapshot(u);
        u.setStatus(UserStatus.DISABLED);
        revokeAllSessions(u, reason);
        String actor = currentActor();
        outbox.publish(AuthEvents.TOPIC, "user:" + u.getId(), AuthEvents.USER_DEACTIVATED,
                new AuthEvents.UserDeactivated(u.getId(), reason.name(), actor));
        audit.emit("user", String.valueOf(u.getId()), "disabled",
                before, snapshot(u), true);
        return UserDtoMapper.of(u);
    }

    /**
     * §7.auth — refuses an action that would leave 0 ACTIVE users with
     * the ADMIN role. Targets: {@link #disable}, {@link #update} when
     * flipping ACTIVE → DISABLED, {@link #detachRole} when removing
     * ADMIN.
     */
    private void guardLastAdmin(User u) {
        boolean isActiveAdmin = u.getStatus() == UserStatus.ACTIVE
                && u.getRoles().stream().anyMatch(r -> "ADMIN".equals(r.getCode()));
        if (!isActiveAdmin) return;
        if (users.countActiveByRoleCode("ADMIN") <= 1) {
            throw new ConflictError(AuthErrorCode.LAST_ADMIN_PROTECTED,
                    "refusing to leave 0 ACTIVE ADMIN users — assign ADMIN to another active user first");
        }
    }

    @Transactional(readOnly = true)
    public UserDto get(long id) {
        return UserDtoMapper.of(loadOrThrow(id));
    }

    @Transactional(readOnly = true)
    public Page<UserDto> list(UserStatus status, String q, String role, Boolean employeeLinked, Pageable pageable) {
        return users.findAll(
                UserSearchSpecifications.search(status, blankToNull(q), blankToNull(role), employeeLinked),
                pageable
        ).map(UserDtoMapper::of);
    }

    private static String blankToNull(String s) {
        return s == null || s.isBlank() ? null : s;
    }

    @Transactional(readOnly = true)
    public UserStatsResponse stats() {
        return users.stats();
    }

    @Transactional
    public UserDto update(long id, UpdateUserRequest req) {
        User u = loadOrThrow(id);
        Map<String, Object> before = snapshot(u);

        if (req.fullName() != null && !req.fullName().isBlank()) {
            if (u.isEmployeeLinked()) {
                throw new ConflictError(AuthErrorCode.EMPLOYEE_LINKED_NAME_READONLY,
                        "full name is managed by employee-service for ESS users");
            }
            u.setFullName(req.fullName());
        }
        if (req.email() != null) {
            if (u.isEmployeeLinked()) {
                throw new ConflictError(AuthErrorCode.EMPLOYEE_LINKED_EMAIL_READONLY,
                        "email is managed by employee-service for ESS users");
            }
            String email = normaliseEmail(req.email());
            if (email != null && !email.equals(u.getEmail())
                    && users.existsByEmailIgnoreCase(email)) {
                throw new ConflictError(AuthErrorCode.EMAIL_TAKEN, "email already in use");
            }
            u.setEmail(email);
        }
        if (req.status() != null && req.status() != u.getStatus()) {
            UserStatus from = u.getStatus();
            UserStatus to = req.status();
            if (to == UserStatus.DISABLED) {
                guardLastAdmin(u);
            }
            u.setStatus(to);
            if (to == UserStatus.DISABLED) {
                revokeAllSessions(u, RevokeReason.ADMIN_REVOKE);
                outbox.publish(AuthEvents.TOPIC, "user:" + u.getId(), AuthEvents.USER_DEACTIVATED,
                        new AuthEvents.UserDeactivated(u.getId(), "ADMIN", currentActor()));
            } else if (from == UserStatus.DISABLED && to == UserStatus.ACTIVE) {
                // §7.auth — emit USER_REACTIVATED so downstream consumers
                // (notification, employee, BFF) flip stale "deactivated"
                // state without waiting for the next event cycle.
                outbox.publish(AuthEvents.TOPIC, "user:" + u.getId(), AuthEvents.USER_REACTIVATED,
                        new AuthEvents.UserReactivated(u.getId(), currentActor()));
            }
        }
        Map<String, Object> after = snapshot(u);
        audit.emit("user", String.valueOf(u.getId()), "updated", before, after, true,
                summarizeUserUpdate(before, after));
        return UserDtoMapper.of(u);
    }

    /**
     * §A2 — one-line summary for the user-visible timeline. Lists which
     * fields actually changed so the row body is informative without the
     * FE having to diff before/after itself ("Email and status updated"
     * beats a bare "User updated").
     */
    private static String summarizeUserUpdate(Map<String, Object> before, Map<String, Object> after) {
        java.util.List<String> changed = new java.util.ArrayList<>();
        for (String key : after.keySet()) {
            if (!java.util.Objects.equals(before.get(key), after.get(key))) {
                changed.add(humanizeField(key));
            }
        }
        if (changed.isEmpty()) return "Profile updated";
        if (changed.size() == 1) return changed.get(0) + " updated";
        return String.join(", ", changed) + " updated";
    }

    private static String humanizeField(String key) {
        return switch (key) {
            case "fullName" -> "Full name";
            case "email" -> "Email";
            case "status" -> "Status";
            case "username" -> "Username";
            case "mustChangePw" -> "Must-change-password flag";
            default -> key;
        };
    }

    @Transactional
    public UserDto attachRoles(long userId, Set<String> roleCodes) {
        User u = loadOrThrow(userId);
        Set<Role> resolved = resolveRoles(roleCodes);
        for (Role r : resolved) {
            if (u.getRoles().add(r)) {
                outbox.publish(AuthEvents.TOPIC, "user:" + u.getId(), AuthEvents.USER_ROLE_ASSIGNED,
                        new AuthEvents.UserRoleAssigned(u.getId(), r.getCode(), currentActor()));
                audit.emit("user", String.valueOf(u.getId()), "role.assigned",
                        null, Map.of("role", r.getCode()), true,
                        "Role " + r.getCode() + " assigned");
            }
        }
        return UserDtoMapper.of(u);
    }

    @Transactional
    public UserDto detachRole(long userId, long roleId) {
        User u = loadOrThrow(userId);
        Role role = roles.findById(roleId)
                .orElseThrow(() -> new NotFoundError(AuthErrorCode.ROLE_NOT_FOUND, "role not found"));
        // §7.auth — last-ADMIN guard on role-strip too.
        if ("ADMIN".equals(role.getCode())
                && u.getStatus() == UserStatus.ACTIVE
                && u.getRoles().contains(role)
                && users.countActiveByRoleCode("ADMIN") <= 1) {
            throw new ConflictError(AuthErrorCode.LAST_ADMIN_PROTECTED,
                    "refusing to strip ADMIN from the last ACTIVE ADMIN");
        }
        if (u.getRoles().remove(role)) {
            outbox.publish(AuthEvents.TOPIC, "user:" + u.getId(), AuthEvents.USER_ROLE_REVOKED,
                    new AuthEvents.UserRoleRevoked(u.getId(), role.getCode(), currentActor()));
            audit.emit("user", String.valueOf(u.getId()), "role.revoked",
                    null, Map.of("role", role.getCode()), true,
                    "Role " + role.getCode() + " revoked");
        }
        return UserDtoMapper.of(u);
    }

    @Transactional
    public void resetPassword(long userId, String temporaryPassword) {
        PasswordPolicy.validate(temporaryPassword);
        User u = loadOrThrow(userId);
        String actor = currentActor();

        u.setPasswordHash(encoder.encode(temporaryPassword));
        u.setMustChangePw(true);
        revokeAllSessions(u, RevokeReason.PASSWORD_CHANGED);

        outbox.publish(AuthEvents.TOPIC, "user:" + u.getId(),
                AuthEvents.USER_PASSWORD_RESET_BY_ADMIN,
                new AuthEvents.UserPasswordResetByAdmin(u.getId(), actor));
        audit.emit("user", String.valueOf(u.getId()), "password.reset_by_admin",
                null, Map.of("by", actor), true);
    }

    /**
     * Bug #7 — re-emits {@code USER_ROLE_ASSIGNED} for every existing
     * (user, role) pair currently in the database. The grant/revoke
     * events fire only on changes going forward, so consumers that
     * came online after grants happened (notification's
     * {@code role_members} projection in particular) start empty.
     * Run this once per fresh deploy of a consumer that needs the
     * projection.
     *
     * <p>Idempotent on the consumer side — the consumer's upsert keys
     * on {@code (role_code, user_id)} so repeated runs are no-ops.
     * Returns the number of (user, role) pairs replayed.
     */
    @Transactional
    public int replayRoleGrants() {
        String actor = currentActor();
        int count = 0;
        for (User u : users.findAll()) {
            for (Role r : u.getRoles()) {
                outbox.publish(AuthEvents.TOPIC, "user:" + u.getId(), AuthEvents.USER_ROLE_ASSIGNED,
                        new AuthEvents.UserRoleAssigned(u.getId(), r.getCode(), actor));
                count++;
            }
        }
        audit.emit("user", "*", "role.grants_replayed",
                null, Map.of("by", actor, "pairs", count), false);
        return count;
    }

    @Transactional
    public void unlock(long userId) {
        User u = loadOrThrow(userId);
        u.setLockedUntil(null);
        u.setFailedLogins(0);
        String actor = currentActor();
        outbox.publish(AuthEvents.TOPIC, "user:" + u.getId(), AuthEvents.USER_UNLOCKED,
                new AuthEvents.UserUnlocked(u.getId(), actor));
        audit.emit("user", String.valueOf(u.getId()), "unlocked",
                null, Map.of("by", actor), false);
    }

    /**
     * Mint a long-lived service-account JWT for {@code userId}. Returned
     * once and not stored. Resource servers accept the token by signature
     * + exp, so rotation is admin-driven: re-issue, re-seal the secret,
     * restart the consumer. Audit-logged with the jti so the issuance is
     * traceable even though the body is not.
     */
    @Transactional
    public ServiceTokenResponse issueServiceToken(long userId, int ttlDays) {
        User u = loadOrThrow(userId);
        if (u.getStatus() == UserStatus.DISABLED) {
            throw new ConflictError(AuthErrorCode.USER_DISABLED,
                    "cannot mint a service token for a DISABLED user");
        }
        JwtIssuer.Issued issued = jwtIssuer.issueServiceToken(u, Duration.ofDays(ttlDays));
        audit.emit("user", String.valueOf(u.getId()), "service_token_issued",
                null,
                Map.of("jti", issued.jti(),
                        "ttlDays", ttlDays,
                        "expiresAt", issued.expiresAt().toString(),
                        "by", currentActor()),
                true);
        return new ServiceTokenResponse(issued.token(), issued.expiresAt(), issued.jti());
    }

    private User loadOrThrow(long id) {
        return users.findById(id)
                .orElseThrow(() -> new NotFoundError(AuthErrorCode.USER_NOT_FOUND, "user not found"));
    }

    private Set<Role> resolveRoles(Set<String> codes) {
        if (codes == null || codes.isEmpty()) return new HashSet<>();
        List<Role> found = roles.findByCodeIn(codes);
        if (found.size() != codes.size()) {
            Set<String> foundCodes = new HashSet<>();
            for (Role r : found) foundCodes.add(r.getCode());
            for (String c : codes) {
                if (!foundCodes.contains(c)) {
                    throw new NotFoundError(AuthErrorCode.ROLE_NOT_FOUND, "unknown role: " + c);
                }
            }
        }
        return new HashSet<>(found);
    }

    private void revokeAllSessions(User u, RevokeReason reason) {
        sessions.revokeAllForUser(u.getId(), LocalDateTime.now(), currentActor(), reason, null);
    }

    private static String currentActor() {
        return CurrentUserHolder.usernameOrSystem();
    }

    /**
     * Lowercase + trim so the {@code lower(email)} unique index in V0014
     * sees a canonical value. Blank input maps to {@code null} so we don't
     * insert empty strings that masquerade as a real email.
     */
    private static String normaliseEmail(String raw) {
        if (raw == null) return null;
        String trimmed = raw.trim();
        return trimmed.isEmpty() ? null : trimmed.toLowerCase(java.util.Locale.ROOT);
    }

    private static Map<String, Object> snapshot(User u) {
        return Map.of(
                "username", u.getUsername(),
                "fullName", u.getFullName(),
                "email", u.getEmail() == null ? "" : u.getEmail(),
                "status", u.getStatus().name(),
                "employeeLinked", u.isEmployeeLinked());
    }
}
