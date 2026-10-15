package com.forward.hrms.gateway.routing;

import com.forward.hrms.commons.testsupport.jwt.JwtTestTokens;

import com.sun.net.httpserver.HttpServer;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.resttestclient.TestRestTemplate;
import org.springframework.boot.resttestclient.autoconfigure.AutoConfigureTestRestTemplate;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Proves route ordering for the {@code /approvals/inbox} → BFF composite
 * alias added ahead of the generic {@code /approvals/**} → attendance
 * route (same specific-before-catch-all pattern as payroll-picker-alias).
 * Stubs both downstreams with plain {@link HttpServer}s so the assertion is
 * on which service actually received the request, not just on HTTP status.
 */
// commons-test pulls Kafka + JPA-entity classes onto the test classpath that
// gateway's main runtime never sees, which flips on OutboxAutoConfiguration's
// @ConditionalOnClass(KafkaTemplate, Entity) and reaches for an
// entityManagerFactory gateway (JPA-less by design) never wires. Same
// doctrine as JPA-less services pulling commons-cache — see CLAUDE.md.
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = "hrms.outbox.enabled=false")
@AutoConfigureTestRestTemplate
class ApprovalsInboxRouteOrderingTest {

    private static final JwtTestTokens TOKENS = new JwtTestTokens();

    private static HttpServer bffStub;
    private static HttpServer attendanceStub;
    private static final AtomicInteger bffHits = new AtomicInteger();
    private static final AtomicInteger attendanceHits = new AtomicInteger();
    private static volatile String lastBffPath;

    @LocalServerPort
    private int port;

    @org.springframework.beans.factory.annotation.Autowired
    private TestRestTemplate restTemplate;

    @DynamicPropertySource
    static void stubDownstreams(DynamicPropertyRegistry registry) throws Exception {
        bffStub = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        bffStub.createContext("/bff/approvals/inbox", ex -> {
            bffHits.incrementAndGet();
            lastBffPath = ex.getRequestURI().getPath();
            respond(ex, "{\"source\":\"bff-composite\"}");
        });
        bffStub.createContext("/bff/approvals/history", ex -> {
            bffHits.incrementAndGet();
            lastBffPath = ex.getRequestURI().getPath();
            respond(ex, "{\"source\":\"bff-composite\"}");
        });
        bffStub.start();

        attendanceStub = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        attendanceStub.createContext("/", ex -> {
            attendanceHits.incrementAndGet();
            respond(ex, "{\"source\":\"attendance\"}");
        });
        attendanceStub.start();

        registry.add("BFF_BASE_URL", () -> "http://127.0.0.1:" + bffStub.getAddress().getPort());
        registry.add("ATTENDANCE_BASE_URL", () -> "http://127.0.0.1:" + attendanceStub.getAddress().getPort());
        registry.add("hrms.jwt.public-key", TOKENS::publicKeyPem);
    }

    @AfterAll
    static void stopStubs() {
        if (bffStub != null) bffStub.stop(0);
        if (attendanceStub != null) attendanceStub.stop(0);
    }

    @BeforeEach
    void resetCounters() {
        bffHits.set(0);
        attendanceHits.set(0);
        lastBffPath = null;
    }

    private static void respond(com.sun.net.httpserver.HttpExchange ex, String body) throws java.io.IOException {
        byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
        ex.getResponseHeaders().add("Content-Type", "application/json");
        ex.sendResponseHeaders(200, bytes.length);
        try (var os = ex.getResponseBody()) {
            os.write(bytes);
        }
    }

    private HttpEntity<Void> authorized() {
        HttpHeaders headers = new HttpHeaders();
        // Gateway requires iss=fshrms-auth / aud=fshrms (application.yml
        // hrms.jwt.issuer/audience) — match what auth-service actually mints.
        String token = TOKENS.issue(1L, "approver", List.of("MANAGER"), List.of(),
                java.time.Duration.ofMinutes(15), "fshrms-auth", "fshrms");
        headers.set("Authorization", "Bearer " + token);
        return new HttpEntity<>(headers);
    }

    @Test
    void approvalsInbox_routesToBffComposite_notAttendance() {
        ResponseEntity<String> resp = restTemplate.exchange(
                "http://localhost:" + port + "/approvals/inbox",
                HttpMethod.GET, authorized(), String.class);

        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(resp.getBody()).contains("bff-composite");
        assertThat(bffHits.get()).isEqualTo(1);
        assertThat(attendanceHits.get()).isZero();
        // RewritePath filter must land on the BFF controller's real mapping.
        assertThat(lastBffPath).isEqualTo("/bff/approvals/inbox");
    }

    @Test
    void approvalsHistory_routesToBffComposite_notAttendance() {
        ResponseEntity<String> resp = restTemplate.exchange(
                "http://localhost:" + port + "/approvals/history?from=2026-07-06&to=2026-08-05",
                HttpMethod.GET, authorized(), String.class);

        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(resp.getBody()).contains("bff-composite");
        assertThat(bffHits.get()).isEqualTo(1);
        assertThat(attendanceHits.get()).isZero();
        // RewritePath filter must land on the BFF controller's real mapping.
        assertThat(lastBffPath).isEqualTo("/bff/approvals/history");
    }

    @Test
    void otherApprovalsSubPaths_stillFallThroughToAttendance() {
        ResponseEntity<String> resp = restTemplate.exchange(
                "http://localhost:" + port + "/approvals/123/decide",
                HttpMethod.GET, authorized(), String.class);

        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(resp.getBody()).contains("attendance");
        assertThat(attendanceHits.get()).isEqualTo(1);
        assertThat(bffHits.get()).isZero();
    }
}
