package com.forward.hrms.auth.integration;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.forward.hrms.auth.entity.Session;
import com.forward.hrms.auth.entity.User;
import com.forward.hrms.auth.enums.UserStatus;
import com.forward.hrms.auth.events.EmployeeEventListener;
import com.forward.hrms.auth.repository.SessionRepository;
import com.forward.hrms.auth.repository.UserRepository;
import com.forward.hrms.commons.kafka.event.EventEnvelope;
import com.forward.hrms.commons.testsupport.containers.WithTestcontainers;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Cross-service contract: auth's listener for {@code employee.events}.
 * Validates the doctrine event shape — {@code {employeeId, userId,
 * changes:{fieldName: newValue}}} for {@code employee.updated} — and
 * the lifecycle reactions (terminate / ESS-revoked → disable user +
 * revoke sessions).
 *
 * <p>Bypasses Kafka delivery: feeds the listener envelope JSON directly
 * via {@code onEmployeeEvent}. The envelope is shaped exactly as
 * {@link EventEnvelope#wrap} would emit, so the dispatch path is the
 * real one.
 */
@SpringBootTest
@WithTestcontainers
class EmployeeEventsConsumerIntegrationTest {

    @Autowired EmployeeEventListener listener;
    @Autowired UserRepository users;
    @Autowired SessionRepository sessions;
    @Autowired PasswordEncoder passwordEncoder;

    @Autowired
    @Qualifier("outboxObjectMapper")
    ObjectMapper json;

    private User seededUser;

    @BeforeEach
    void seedUser() {
        sessions.deleteAll();
        users.findByUsername("denorm-test-user").ifPresent(users::delete);
        users.flush();

        User u = new User();
        u.setUsername("denorm-test-user");
        u.setFullName("Original Name");
        u.setPasswordHash(passwordEncoder.encode("ignored-for-this-test"));
        u.setStatus(UserStatus.ACTIVE);
        u.setMustChangePw(false);
        u.setEmployeeLinked(true);
        seededUser = users.save(u);
    }

    @Test
    void employeeUpdated_syncsFullName_whenChangesIncludesField() throws Exception {
        Map<String, Object> changes = Map.of("fullName", "Renamed Person");
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("employeeId", 4242L);
        payload.put("userId", seededUser.getId());
        payload.put("changes", changes);

        listener.onEmployeeEvent(envelope("employee.updated", payload));

        User reloaded = users.findById(seededUser.getId()).orElseThrow();
        assertThat(reloaded.getFullName()).isEqualTo("Renamed Person");
    }

    @Test
    void employeeUpdated_withoutFullNameInChanges_isANoop() throws Exception {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("employeeId", 4243L);
        payload.put("userId", seededUser.getId());
        payload.put("changes", Map.of("departmentId", 7L));

        listener.onEmployeeEvent(envelope("employee.updated", payload));

        User reloaded = users.findById(seededUser.getId()).orElseThrow();
        assertThat(reloaded.getFullName()).isEqualTo("Original Name");
    }

    @Test
    void employeeUpdated_withUnknownUserId_warnsAndReturns() throws Exception {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("employeeId", 4244L);
        payload.put("userId", 999_999L);
        payload.put("changes", Map.of("fullName", "Ghost"));

        // Should not throw — the listener logs a warn and returns.
        listener.onEmployeeEvent(envelope("employee.updated", payload));

        // The seeded user remains untouched.
        User reloaded = users.findById(seededUser.getId()).orElseThrow();
        assertThat(reloaded.getFullName()).isEqualTo("Original Name");
    }

    @Test
    void employeeTerminated_disablesUser_andRevokesSessions() throws Exception {
        Session s = new Session();
        s.setUserId(seededUser.getId());
        s.setRefreshTokenId("test-rt-" + System.nanoTime());
        s.setIssuedAt(LocalDateTime.now());
        s.setLastSeenAt(LocalDateTime.now());
        s.setExpiresAt(LocalDateTime.now().plusDays(1));
        sessions.save(s);

        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("employeeId", 4245L);
        payload.put("userId", seededUser.getId());
        payload.put("exitType", "TERMINATION");
        payload.put("lastWorkingDay", LocalDate.now().toString());
        payload.put("reason", "policy violation");

        listener.onEmployeeEvent(envelope("employee.terminated", payload));

        User reloaded = users.findById(seededUser.getId()).orElseThrow();
        assertThat(reloaded.getStatus()).isEqualTo(UserStatus.DISABLED);

        Session revoked = sessions.findById(s.getId()).orElseThrow();
        assertThat(revoked.getRevokedAt()).isNotNull();
        assertThat(revoked.getRevokedReason().name()).isEqualTo("EMPLOYEE_TERMINATED");
    }

    @Test
    void employeeEssRevoked_disablesUser_andRevokesSessions() throws Exception {
        Session s = new Session();
        s.setUserId(seededUser.getId());
        s.setRefreshTokenId("test-rt-" + System.nanoTime());
        s.setIssuedAt(LocalDateTime.now());
        s.setLastSeenAt(LocalDateTime.now());
        s.setExpiresAt(LocalDateTime.now().plusDays(1));
        sessions.save(s);

        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("employeeId", 4246L);
        payload.put("userId", seededUser.getId());
        payload.put("by", "hr-admin");

        listener.onEmployeeEvent(envelope("employee.ess_revoked", payload));

        User reloaded = users.findById(seededUser.getId()).orElseThrow();
        assertThat(reloaded.getStatus()).isEqualTo(UserStatus.DISABLED);

        Session revoked = sessions.findById(s.getId()).orElseThrow();
        assertThat(revoked.getRevokedAt()).isNotNull();
        assertThat(revoked.getRevokedReason().name()).isEqualTo("EMPLOYEE_ESS_REVOKED");
    }

    private String envelope(String eventType, Map<String, Object> payload) throws Exception {
        EventEnvelope<Map<String, Object>> env = new EventEnvelope<>(
                java.util.UUID.randomUUID().toString(),
                eventType,
                LocalDateTime.now(),
                null,
                null,
                null,
                null,
                null,
                payload);
        return json.writeValueAsString(env);
    }
}
