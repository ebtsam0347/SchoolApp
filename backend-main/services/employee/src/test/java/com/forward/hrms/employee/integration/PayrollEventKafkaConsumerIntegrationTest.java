package com.forward.hrms.employee.integration;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.forward.hrms.commons.core.context.CurrentUser;
import com.forward.hrms.commons.core.context.CurrentUserHolder;
import com.forward.hrms.commons.testsupport.containers.WithTestcontainers;
import com.forward.hrms.employee.client.AuthClient;
import com.forward.hrms.employee.entity.Employee;
import com.forward.hrms.employee.entity.EmployeeExit;
import com.forward.hrms.employee.entity.EmployeeHistory;
import com.forward.hrms.employee.entity.EmploymentContract;
import com.forward.hrms.employee.enums.ContractStatus;
import com.forward.hrms.employee.enums.ContractType;
import com.forward.hrms.employee.enums.EmploymentStatus;
import com.forward.hrms.employee.enums.ExitStatus;
import com.forward.hrms.employee.enums.HistoryChangeReason;
import com.forward.hrms.employee.events.payroll.PayrollEvents;
import com.forward.hrms.commons.kafka.event.EventEnvelope;
import com.forward.hrms.employee.repository.EmployeeExitRepository;
import com.forward.hrms.employee.repository.EmployeeHistoryRepository;
import com.forward.hrms.employee.repository.EmployeeRepository;
import com.forward.hrms.employee.repository.EmploymentContractRepository;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Set;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Supplier;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doNothing;

/**
 * The real messaging-path proof for Goal 3: publishes genuine Kafka
 * records to the shared {@code payroll.events} topic and lets
 * employee-service's real, already-running {@code @KafkaListener}
 * ({@link com.forward.hrms.employee.events.payroll.PayrollEventListener})
 * consume and dispatch them — driving {@code ExitService} and
 * {@code TerminationSaga} exactly as production Kafka delivery would.
 * This test never calls {@code PayrollEventListener}, {@code
 * ExitService.undoTermination}, or {@code TerminationSaga.undoTermination}
 * directly; every assertion is against database state reached only
 * because the real consumer processed a real record.
 *
 * <p><b>What is genuinely real:</b> the Kafka broker (shared Testcontainer,
 * same one {@code FinalSettlementCancelKafkaProducerIntegrationTest} uses
 * on the payroll side), the {@code KafkaTemplate} producer, the
 * {@code @KafkaListener} consumer container, JSON deserialization via the
 * same {@code outboxObjectMapper}/{@code EventEnvelope} wire format
 * payroll's own {@code Outbox.publish} uses, and every downstream
 * ExitService/TerminationSaga/repository call the listener triggers.
 *
 * <p><b>What is bridged, not real:</b> this test constructs the message
 * content itself rather than driving it out of a live payroll-service
 * process — a single JUnit test can only boot one service's Spring
 * context, so a literal single-process dual-service boot is out of
 * scope for this task. The producer half (Outbox row -> OutboxPublisher
 * -> real Kafka publish, with the exact same record shape) is proved for
 * real, separately, in {@code FinalSettlementCancelKafkaProducerIntegrationTest}.
 * Together the two tests genuinely exercise every hop in the chain; only
 * the seam between "message published by payroll" and "message consumed
 * by employee" is bridged by test code using the identical wire format
 * both real services use.
 *
 * <p><b>Known environment hazard (not a code defect):</b> in this
 * project's current sandboxed dev environment, {@code SharedContainers
 * .KAFKA}'s advertised bootstrap address resolves to {@code
 * localhost:9092} — the exact same port the long-running
 * {@code docker-compose} dev stack's own {@code backend-kafka-1}
 * container publishes on. That means this "Testcontainers" broker is
 * not actually isolated from the persistent dev stack in this
 * environment: the {@code payroll.events} topic and the
 * {@code employee.payroll-events} consumer group accumulate real
 * history across every separate test run (and potentially the live dev
 * stack's own traffic), rather than starting empty each time as a truly
 * ephemeral Testcontainer would. A stuck/unprocessable backlog record on
 * that consumer group (e.g. a FINAL_SETTLEMENT_CREATED for an
 * employeeId that only ever existed in a payroll-only test fixture)
 * blocks this consumer group's single partition behind the project's
 * real exponential backoff (60s, then 4x per attempt, up to a 1h cap
 * and 6h total budget before DLQ — see {@code RetryAndDlqErrorHandler}
 * in commons-kafka) exactly as it would in production, which no
 * reasonable test timeout can wait out. This is an environment-isolation
 * gap, not a defect in this test or in the production listener/retry
 * config — see the final report for what was and wasn't verified as a
 * result.
 */
@SpringBootTest
@WithTestcontainers
class PayrollEventKafkaConsumerIntegrationTest {

    @Autowired KafkaTemplate<String, String> kafkaTemplate;
    @Autowired
    @Qualifier("outboxObjectMapper")
    ObjectMapper outboxJson;

    @Autowired EmployeeRepository employees;
    @Autowired EmployeeExitRepository exits;
    @Autowired EmploymentContractRepository contracts;
    @Autowired EmployeeHistoryRepository histories;

    @MockitoBean AuthClient authClient;

    // Seeded from nanoTime, not a fixed literal — Postgres data persists
    // across separate test-JVM runs in this environment (Testcontainers
    // reuse), so a fixed starting value collides with employees.user_id's
    // unique constraint on rerun.
    private static final AtomicLong NEXT_USER_ID = new AtomicLong(System.nanoTime() & 0x7fffffffL);

    @BeforeEach
    void mockAuthAndActor() {
        doNothing().when(authClient).disableUser(anyLong(), any(), anyString());
        doNothing().when(authClient).enableUser(anyLong(), any());
        CurrentUserHolder.set(new CurrentUser(1L, "hr-tester", Set.of(), Set.of("employee.write")));
    }

    @AfterEach
    void clearActor() {
        CurrentUserHolder.clear();
    }

    @Test
    void cancelEvent_throughRealKafka_restoresEmployment_contract_history_exit() {
        long userId = NEXT_USER_ID.getAndIncrement();
        long employeeId = persistActiveEmployeeWithUser(userId);
        EmploymentContract contract = seedActiveContract(employeeId);
        EmployeeHistory history = seedOpenHistory(employeeId);

        long settlementId = uniqueSettlementId();
        LocalDate lwd = LocalDate.now();

        // ---------- Real Kafka: FINAL_SETTLEMENT_CREATED ----------
        publish(PayrollEvents.FINAL_SETTLEMENT_CREATED, new PayrollEvents.FinalSettlementCreated(
                settlementId, employeeId, "EMP-" + employeeId, "Kafka Round Trip Test",
                lwd, "termination settlement", BigDecimal.valueOf(10000),
                lwd, 4242L));

        await(() -> employees.findById(employeeId).map(Employee::getEmploymentStatus).orElse(null),
                EmploymentStatus.TERMINATED, "employee terminated after real CREATED event");

        EmploymentContract contractAfterTerm = contracts.findById(contract.getId()).orElseThrow();
        assertThat(contractAfterTerm.getStatus()).isEqualTo(ContractStatus.TERMINATED);
        assertThat(contractAfterTerm.getEndDate()).isEqualTo(lwd);

        EmployeeHistory historyAfterTerm = histories.findById(history.getId()).orElseThrow();
        assertThat(historyAfterTerm.getEffectiveTo()).isEqualTo(lwd);
        assertThat(historyAfterTerm.getChangeReason()).isEqualTo(HistoryChangeReason.EXIT);

        EmployeeExit exitAfterTerm = exits.findBySettlementId(settlementId).orElseThrow();
        assertThat(exitAfterTerm.getStatus()).isEqualTo(ExitStatus.COMPLETED);

        // ---------- Real Kafka: FINAL_SETTLEMENT_CANCELLED ----------
        publish(PayrollEvents.FINAL_SETTLEMENT_CANCELLED, new PayrollEvents.FinalSettlementCancelled(
                settlementId, employeeId, "EMP-" + employeeId, "Kafka Round Trip Test",
                lwd, "HR data entry mistake", LocalDateTime.now(), 4242L));

        await(() -> employees.findById(employeeId).map(Employee::getEmploymentStatus).orElse(null),
                EmploymentStatus.ACTIVE, "employee restored to ACTIVE after real CANCELLED event");

        EmploymentContract contractAfterCancel = contracts.findById(contract.getId()).orElseThrow();
        assertThat(contractAfterCancel.getStatus()).isEqualTo(ContractStatus.ACTIVE);
        assertThat(contractAfterCancel.getEndDate()).isNull();

        EmployeeHistory historyAfterCancel = histories.findById(history.getId()).orElseThrow();
        assertThat(historyAfterCancel.getEffectiveTo()).isNull();
        assertThat(historyAfterCancel.getChangeReason()).isEqualTo(HistoryChangeReason.REINSTATED);

        EmployeeExit exitAfterCancel = exits.findBySettlementId(settlementId).orElseThrow();
        assertThat(exitAfterCancel.getStatus()).isEqualTo(ExitStatus.WITHDRAWN);
        // Same exit row, same settlementId correlation — never repointed.
        assertThat(exitAfterCancel.getId()).isEqualTo(exitAfterTerm.getId());
        assertThat(exitAfterCancel.getSettlementId()).isEqualTo(settlementId);
    }

    /** Secondary, per the task's priority note: one duplicate is enough. */
    @Test
    void duplicateCancelEvent_throughRealKafka_isNoOp() {
        long employeeId = persistActiveEmployeeWithUser(NEXT_USER_ID.getAndIncrement());
        long settlementId = uniqueSettlementId();
        LocalDate lwd = LocalDate.now();

        publish(PayrollEvents.FINAL_SETTLEMENT_CREATED, new PayrollEvents.FinalSettlementCreated(
                settlementId, employeeId, "EMP-" + employeeId, "Dup Cancel Test",
                lwd, "reason", BigDecimal.ZERO, lwd, 4242L));
        await(() -> employees.findById(employeeId).map(Employee::getEmploymentStatus).orElse(null),
                EmploymentStatus.TERMINATED, "terminated before duplicate-cancel test");

        PayrollEvents.FinalSettlementCancelled cancelled = new PayrollEvents.FinalSettlementCancelled(
                settlementId, employeeId, "EMP-" + employeeId, "Dup Cancel Test",
                lwd, "HR mistake", LocalDateTime.now(), 4242L);
        publish(PayrollEvents.FINAL_SETTLEMENT_CANCELLED, cancelled);
        await(() -> employees.findById(employeeId).map(Employee::getEmploymentStatus).orElse(null),
                EmploymentStatus.ACTIVE, "active after first real CANCELLED event");

        // Duplicate delivery of the exact same event — must not throw and
        // must not change anything further.
        publish(PayrollEvents.FINAL_SETTLEMENT_CANCELLED, cancelled);
        // No exception propagates out of publish(); give the (no-op)
        // second delivery a moment to be processed, then assert state is
        // unchanged.
        sleep(Duration.ofSeconds(2));

        assertThat(employees.findById(employeeId).orElseThrow().getEmploymentStatus())
                .isEqualTo(EmploymentStatus.ACTIVE);
        assertThat(exits.findBySettlementId(settlementId).orElseThrow().getStatus())
                .isEqualTo(ExitStatus.WITHDRAWN);
    }

    /**
     * Historical multi-settlement safety, driven through real Kafka:
     * S1 CREATE -> CANCEL (E1 -> WITHDRAWN), S2 CREATE (E2, new row), then
     * a LATE duplicate S1 CANCELLED event must act only on E1 — E2 must
     * remain untouched and still correlated to S2.
     */
    @Test
    void lateS1CancelledEvent_throughRealKafka_neverTouchesS2() {
        long employeeId = persistActiveEmployeeWithUser(NEXT_USER_ID.getAndIncrement());
        LocalDate lwd = LocalDate.now();
        long settlementOne = uniqueSettlementId();
        long settlementTwo = uniqueSettlementId();

        publish(PayrollEvents.FINAL_SETTLEMENT_CREATED, new PayrollEvents.FinalSettlementCreated(
                settlementOne, employeeId, "EMP-" + employeeId, "Multi Settlement Test",
                lwd, "reason", BigDecimal.ZERO, lwd, 4242L));
        await(() -> employees.findById(employeeId).map(Employee::getEmploymentStatus).orElse(null),
                EmploymentStatus.TERMINATED, "terminated by S1");

        PayrollEvents.FinalSettlementCancelled s1Cancelled = new PayrollEvents.FinalSettlementCancelled(
                settlementOne, employeeId, "EMP-" + employeeId, "Multi Settlement Test",
                lwd, "wrong settlement", LocalDateTime.now(), 4242L);
        publish(PayrollEvents.FINAL_SETTLEMENT_CANCELLED, s1Cancelled);
        await(() -> exits.findBySettlementId(settlementOne).map(EmployeeExit::getStatus).orElse(null),
                ExitStatus.WITHDRAWN, "S1 exit withdrawn");

        publish(PayrollEvents.FINAL_SETTLEMENT_CREATED, new PayrollEvents.FinalSettlementCreated(
                settlementTwo, employeeId, "EMP-" + employeeId, "Multi Settlement Test",
                lwd.plusDays(1), "reason 2", BigDecimal.ZERO, lwd, 4242L));
        await(() -> exits.findBySettlementId(settlementTwo).map(EmployeeExit::getStatus).orElse(null),
                ExitStatus.COMPLETED, "S2 exit completed");

        long e1Id = exits.findBySettlementId(settlementOne).orElseThrow().getId();
        long e2Id = exits.findBySettlementId(settlementTwo).orElseThrow().getId();
        assertThat(e1Id).isNotEqualTo(e2Id);

        // Late/duplicate S1 CANCELLED arrives after S2 already exists.
        publish(PayrollEvents.FINAL_SETTLEMENT_CANCELLED, s1Cancelled);
        sleep(Duration.ofSeconds(2));

        EmployeeExit e1 = exits.findBySettlementId(settlementOne).orElseThrow();
        assertThat(e1.getId()).isEqualTo(e1Id);
        assertThat(e1.getStatus()).isEqualTo(ExitStatus.WITHDRAWN);

        EmployeeExit e2 = exits.findBySettlementId(settlementTwo).orElseThrow();
        assertThat(e2.getId()).isEqualTo(e2Id);
        assertThat(e2.getStatus()).isEqualTo(ExitStatus.COMPLETED);
        assertThat(e2.getSettlementId()).isEqualTo(settlementTwo);

        assertThat(employees.findById(employeeId).orElseThrow().getEmploymentStatus())
                .isEqualTo(EmploymentStatus.TERMINATED);
    }

    // ---------------------------------------------------------------- helpers

    private void publish(String eventType, Object payload) {
        String json;
        try {
            json = outboxJson.writeValueAsString(EventEnvelope.wrap(eventType, payload));
        } catch (Exception ex) {
            throw new IllegalStateException("failed to serialize test event", ex);
        }
        kafkaTemplate.send(PayrollEvents.TOPIC, "final_settlement:kafka-consumer-test", json);
    }

    private <T> void await(Supplier<T> actual, T expected, String description) {
        long deadline = System.nanoTime() + Duration.ofSeconds(20).toNanos();
        T last = null;
        while (System.nanoTime() < deadline) {
            last = actual.get();
            if (expected.equals(last)) return;
            sleep(Duration.ofMillis(300));
        }
        throw new AssertionError("Timed out waiting for: " + description
                + " — last observed value: " + last);
    }

    private void sleep(Duration d) {
        try {
            Thread.sleep(d.toMillis());
        } catch (InterruptedException ie) {
            Thread.currentThread().interrupt();
        }
    }

    private long persistActiveEmployeeWithUser(long userId) {
        Employee e = new Employee();
        e.setEmployeeCode("KAFKA-" + System.nanoTime());
        e.setFullName("Kafka Round Trip Test");
        e.setJoiningDate(LocalDate.now().minusYears(1));
        e.setEmploymentStatus(EmploymentStatus.ACTIVE);
        e.setUserId(userId);
        return employees.save(e).getId();
    }

    private EmploymentContract seedActiveContract(long employeeId) {
        EmploymentContract c = new EmploymentContract();
        c.setEmployeeId(employeeId);
        c.setContractType(ContractType.PERMANENT);
        c.setStartDate(LocalDate.now().minusYears(1));
        c.setStatus(ContractStatus.ACTIVE);
        return contracts.save(c);
    }

    private EmployeeHistory seedOpenHistory(long employeeId) {
        EmployeeHistory h = new EmployeeHistory();
        h.setEmployeeId(employeeId);
        h.setEffectiveFrom(LocalDate.now().minusYears(1));
        h.setEffectiveTo(null);
        h.setChangeReason(HistoryChangeReason.ONBOARD);
        return histories.save(h);
    }

    private long uniqueSettlementId() {
        return System.nanoTime() & 0x7fffffffffL;
    }
}
