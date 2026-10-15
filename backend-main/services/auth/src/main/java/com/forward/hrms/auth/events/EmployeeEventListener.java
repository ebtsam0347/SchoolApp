package com.forward.hrms.auth.events;

import java.time.LocalDateTime;
import java.util.Map;
import java.util.Optional;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.forward.hrms.auth.entity.User;
import com.forward.hrms.auth.enums.RevokeReason;
import com.forward.hrms.auth.enums.UserStatus;
import com.forward.hrms.auth.repository.SessionRepository;
import com.forward.hrms.auth.repository.UserRepository;
import com.forward.hrms.commons.employeeevents.EmployeeEvents;
import com.forward.hrms.commons.employeeevents.EmployeeEventsConsumerSupport;
import com.forward.hrms.commons.kafka.outbox.AuditEmitter;
import com.forward.hrms.commons.kafka.outbox.Outbox;

import lombok.extern.slf4j.Slf4j;


@Slf4j
@Component
public class EmployeeEventListener extends EmployeeEventsConsumerSupport {

    private final UserRepository users;
    private final SessionRepository sessions;
    private final Outbox outbox;
    private final AuditEmitter audit;

    public EmployeeEventListener(UserRepository users,
                                 SessionRepository sessions,
                                 Outbox outbox,
                                 AuditEmitter audit,
                                 @Qualifier("outboxObjectMapper") ObjectMapper json) {
        super(json);
        this.users = users;
        this.sessions = sessions;
        this.outbox = outbox;
        this.audit = audit;
    }

    @KafkaListener(topics = EmployeeEvents.TOPIC, groupId = "auth.employee-events")
    @Transactional
    public void onEmployeeEvent(String message) throws java.io.IOException {
        dispatch(message);
    }

    @Override
    protected void handle(String eventType, JsonNode payload) {
        switch (eventType) {
            case EmployeeEvents.EMPLOYEE_UPDATED -> handleUpdated(payload);
            case EmployeeEvents.EMPLOYEE_TERMINATED -> handleTerminated(payload);
            case EmployeeEvents.EMPLOYEE_REINSTATED -> handleReinstated(payload);
            case EmployeeEvents.EMPLOYEE_ESS_REVOKED -> handleEssRevoked(payload);
            default -> log.debug("Ignoring employee event type {}", eventType);
        }
    }

    private void handleUpdated(JsonNode p) {
        Long userId = readLong(p, "userId");
        if (userId == null) return; // employee not ESS-linked
        Optional<User> opt = users.findById(userId);
        if (opt.isEmpty()) {
            log.warn("employee.updated for unknown auth user_id={} (employeeId={})",
                    userId, p.path("employeeId").asLong());
            return;
        }
        User u = opt.get();
        JsonNode changes = p.path("changes");
        if (!changes.isObject()) return;
        String full = changes.path("fullName").asText("");
        if (full.isEmpty() || full.equals(u.getFullName())) return;
        u.setFullName(full);
        outbox.publish(AuthEvents.TOPIC, "user:" + u.getId(), AuthEvents.USER_FULL_NAME_SYNCED,
                new AuthEvents.UserFullNameSynced(u.getId(), full));
        audit.emit("user", String.valueOf(u.getId()), "full_name.synced",
                null, Map.of("fullName", full), false);
    }

    private void handleTerminated(JsonNode p) {
        disableLinkedUser(p, RevokeReason.EMPLOYEE_TERMINATED, "EMPLOYEE_TERMINATED");
    }

    /**
     * Dual-path resilience: TerminationUndoSaga already calls
     * {@code PATCH /admin/users/{id}} directly, but this Kafka consumer
     * mirrors it (like {@code handleTerminated}) in case the direct saga
     * call is skipped or fails to reach auth-service.
     */
    private void handleReinstated(JsonNode p) {
        Long userId = readLong(p, "userId");
        if (userId == null) return;
        Optional<User> opt = users.findById(userId);
        if (opt.isEmpty()) {
            log.warn("employee.reinstated for unknown auth user_id={} (employeeId={})",
                    userId, p.path("employeeId").asLong());
            return;
        }
        User u = opt.get();
        if (u.getStatus() == UserStatus.DISABLED) {
            u.setStatus(UserStatus.ACTIVE);
            outbox.publish(AuthEvents.TOPIC, "user:" + u.getId(), AuthEvents.USER_REACTIVATED,
                    new AuthEvents.UserReactivated(u.getId(), "system"));
            audit.emit("user", String.valueOf(u.getId()), "reactivated",
                    null, Map.of("reason", "EMPLOYEE_REINSTATED"), true);
        }
    }

    private void handleEssRevoked(JsonNode p) {
        disableLinkedUser(p, RevokeReason.EMPLOYEE_ESS_REVOKED, "EMPLOYEE_ESS_REVOKED");
    }

    private void disableLinkedUser(JsonNode p, RevokeReason reason, String reasonLabel) {
        Long userId = readLong(p, "userId");
        if (userId == null) return;
        Optional<User> opt = users.findById(userId);
        if (opt.isEmpty()) {
            log.warn("{} for unknown auth user_id={} (employeeId={})",
                    reasonLabel, userId, p.path("employeeId").asLong());
            return;
        }
        User u = opt.get();
        boolean changed = false;
        if (u.getStatus() != UserStatus.DISABLED) {
            u.setStatus(UserStatus.DISABLED);
            changed = true;
        }
        int revoked = sessions.revokeAllForUser(u.getId(), LocalDateTime.now(), "system", reason, null);
        if (changed) {
            outbox.publish(AuthEvents.TOPIC, "user:" + u.getId(), AuthEvents.USER_DEACTIVATED,
                    new AuthEvents.UserDeactivated(u.getId(), reasonLabel, "system"));
            audit.emit("user", String.valueOf(u.getId()), "deactivated",
                    null, Map.of("reason", reasonLabel, "sessionsRevoked", revoked), true);
        }
    }
}
