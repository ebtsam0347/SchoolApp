package com.forward.hrms.employee.integration;

import com.forward.hrms.commons.core.context.CurrentUser;
import com.forward.hrms.commons.core.context.CurrentUserHolder;
import com.forward.hrms.commons.saga.SagaRunner;
import com.forward.hrms.commons.saga.SagaService;
import com.forward.hrms.commons.saga.SagaState;
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
import com.forward.hrms.employee.repository.EmployeeExitRepository;
import com.forward.hrms.employee.repository.EmployeeHistoryRepository;
import com.forward.hrms.employee.repository.EmployeeRepository;
import com.forward.hrms.employee.repository.EmploymentContractRepository;
import com.forward.hrms.employee.service.ExitService;
import com.forward.hrms.employee.service.TerminationDisableSaga;
import com.forward.hrms.employee.service.TerminationUndoSaga;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import java.time.LocalDate;
import java.util.Set;
import java.util.concurrent.atomic.AtomicLong;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

/**
 * Closes the two verification gaps the prior audit flagged as "not fully
 * proven": auth restoration for an employee with a real userId, and
 * EmploymentContract/EmployeeHistory reversal — both asserted against
 * real persisted database state, not just EmployeeExit or a mock
 * invocation count.
 *
 * <p>Drives ExitService directly (createPendingExit / undoTermination),
 * not through Kafka — that real-messaging-path proof is a separate,
 * dedicated test (PayrollEventKafkaConsumerIntegrationTest). This class
 * exists to prove the business logic and the auth saga's own persisted
 * state deterministically, without Kafka timing involved.
 *
 * <p>Auth-state honesty: there is no auth-service Testcontainer in this
 * project's test infrastructure (confirmed — {@code SharedContainers}
 * wires only Postgres/Redis/Kafka/MinIO). {@code AuthClient} is mocked
 * at the HTTP boundary, exactly as every existing auth-adjacent test in
 * this codebase already does (see TerminationSagaStepIntegrationTest).
 * What IS real and asserted here: the employee has an actual non-null
 * userId (so the {@code employee.getUserId() != null} gate in
 * TerminationSaga actually fires — it does not in the historical-model
 * verification tests, which never set userId); the
 * TERMINATION_DISABLE_AUTH / TERMINATION_UNDO_AUTH saga rows are real,
 * persisted {@code saga_state} rows (queried back from Postgres, not
 * asserted from memory); and the saga is driven to completion via
 * {@link SagaRunner#advanceOne}, the same production method the
 * scheduled poller calls, so the full internal chain (saga created →
 * step executed → AuthClient invoked with the correct userId → saga
 * marked COMPLETED) is exercised for real. The one thing this cannot
 * reach — because no such infrastructure exists in this project — is a
 * real auth-service "enabled/disabled" flag; that boundary is mocked.
 */
// The background SagaRunner.poll() scheduler (default every 2s) would
// otherwise race with this test's own deterministic sagaRunner.advanceOne()
// calls — both are valid callers of the same real advanceOne() method, but
// production only ever has one (the scheduler, sequentially); a test
// driving the same saga id concurrently with it is a test-timing hazard,
// not a production defect, since advanceOne() has no pessimistic lock and
// was never designed to be called from two callers at once. Push the
// scheduler's interval out far past this test's runtime so only the
// explicit advanceOne() calls below drive each saga.
@SpringBootTest(properties = "hrms.saga.poll-delay=PT1H")
@WithTestcontainers
class TerminationReversalDomainStateIntegrationTest {

    @Autowired ExitService exitService;
    @Autowired EmployeeRepository employees;
    @Autowired EmployeeExitRepository exits;
    @Autowired EmploymentContractRepository contracts;
    @Autowired EmployeeHistoryRepository histories;
    @Autowired SagaService sagaService;
    @Autowired SagaRunner sagaRunner;

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
    void cancelAfterTermination_restoresEmployment_contract_history_exit_and_authSaga() {
        long userId = NEXT_USER_ID.getAndIncrement();
        long employeeId = persistActiveEmployeeWithUser(userId);
        EmploymentContract contract = seedActiveContract(employeeId);
        EmployeeHistory history = seedOpenHistory(employeeId);

        long settlementId = uniqueSettlementId();
        LocalDate lwd = LocalDate.now();

        // ---------- Before: active baseline ----------
        assertThat(employees.findById(employeeId).orElseThrow().getEmploymentStatus())
                .isEqualTo(EmploymentStatus.ACTIVE);
        assertThat(contracts.findById(contract.getId()).orElseThrow().getStatus())
                .isEqualTo(ContractStatus.ACTIVE);
        assertThat(histories.findById(history.getId()).orElseThrow().getEffectiveTo()).isNull();

        // ---------- Termination (settlement CREATE) ----------
        exitService.createPendingExit(settlementId, employeeId, lwd, "termination settlement");

        assertThat(employees.findById(employeeId).orElseThrow().getEmploymentStatus())
                .isEqualTo(EmploymentStatus.TERMINATED);

        EmploymentContract contractAfterTerm = contracts.findById(contract.getId()).orElseThrow();
        assertThat(contractAfterTerm.getStatus()).isEqualTo(ContractStatus.TERMINATED);
        assertThat(contractAfterTerm.getEndDate()).isEqualTo(lwd);

        EmployeeHistory historyAfterTerm = histories.findById(history.getId()).orElseThrow();
        assertThat(historyAfterTerm.getEffectiveTo()).isEqualTo(lwd);
        assertThat(historyAfterTerm.getChangeReason()).isEqualTo(HistoryChangeReason.EXIT);

        EmployeeExit exitAfterTerm = exits.findBySettlementId(settlementId).orElseThrow();
        assertThat(exitAfterTerm.getStatus()).isEqualTo(ExitStatus.COMPLETED);
        assertThat(exitAfterTerm.getSettlementId()).isEqualTo(settlementId);

        // ---------- Auth disable: real persisted saga state ----------
        SagaState disableSaga = sagaService
                .findByKey(TerminationDisableSaga.NAME, "employee:" + employeeId)
                .orElseThrow(() -> new AssertionError("no TERMINATION_DISABLE_AUTH saga row persisted"));
        assertThat(disableSaga.getStatus()).isEqualTo(SagaState.Status.RUNNING);

        sagaRunner.advanceOne(disableSaga.getId());

        SagaState disableSagaDone = sagaService
                .findByKey(TerminationDisableSaga.NAME, "employee:" + employeeId).orElseThrow();
        assertThat(disableSagaDone.getStatus()).isEqualTo(SagaState.Status.COMPLETED);
        verify(authClient, times(1)).disableUser(eq(userId), any(), eq("EMPLOYEE_TERMINATED"));

        // ---------- CANCEL / reversal ----------
        exitService.undoTermination(employeeId, settlementId, "HR data entry mistake", "cancelled");

        assertThat(employees.findById(employeeId).orElseThrow().getEmploymentStatus())
                .isEqualTo(EmploymentStatus.ACTIVE);

        EmploymentContract contractAfterUndo = contracts.findById(contract.getId()).orElseThrow();
        assertThat(contractAfterUndo.getStatus()).isEqualTo(ContractStatus.ACTIVE);
        assertThat(contractAfterUndo.getEndDate()).isNull();

        EmployeeHistory historyAfterUndo = histories.findById(history.getId()).orElseThrow();
        assertThat(historyAfterUndo.getEffectiveTo()).isNull();
        assertThat(historyAfterUndo.getChangeReason()).isEqualTo(HistoryChangeReason.REINSTATED);

        EmployeeExit exitAfterUndo = exits.findBySettlementId(settlementId).orElseThrow();
        assertThat(exitAfterUndo.getStatus()).isEqualTo(ExitStatus.WITHDRAWN);
        // Still correlated to the SAME settlement — never repointed.
        assertThat(exitAfterUndo.getSettlementId()).isEqualTo(settlementId);
        assertThat(exitAfterUndo.getId()).isEqualTo(exitAfterTerm.getId());

        // ---------- Auth re-enable: real persisted saga state ----------
        SagaState undoSaga = sagaService
                .findByKey(TerminationUndoSaga.NAME, "employee:" + employeeId)
                .orElseThrow(() -> new AssertionError("no TERMINATION_UNDO_AUTH saga row persisted"));
        assertThat(undoSaga.getStatus()).isEqualTo(SagaState.Status.RUNNING);

        sagaRunner.advanceOne(undoSaga.getId());

        SagaState undoSagaDone = sagaService
                .findByKey(TerminationUndoSaga.NAME, "employee:" + employeeId).orElseThrow();
        assertThat(undoSagaDone.getStatus()).isEqualTo(SagaState.Status.COMPLETED);
        verify(authClient, times(1)).enableUser(eq(userId), any());
    }

    @Test
    void rejectAfterTermination_restoresAuthSagaAndEmploymentStatus() {
        long userId = NEXT_USER_ID.getAndIncrement();
        long employeeId = persistActiveEmployeeWithUser(userId);
        long settlementId = uniqueSettlementId();
        LocalDate lwd = LocalDate.now();

        exitService.createPendingExit(settlementId, employeeId, lwd, "termination settlement");
        assertThat(employees.findById(employeeId).orElseThrow().getEmploymentStatus())
                .isEqualTo(EmploymentStatus.TERMINATED);

        SagaState disableSaga = sagaService
                .findByKey(TerminationDisableSaga.NAME, "employee:" + employeeId).orElseThrow();
        sagaRunner.advanceOne(disableSaga.getId());
        verify(authClient, times(1)).disableUser(eq(userId), any(), eq("EMPLOYEE_TERMINATED"));

        exitService.undoTermination(employeeId, settlementId, "wrong employee", "rejected");

        assertThat(employees.findById(employeeId).orElseThrow().getEmploymentStatus())
                .isEqualTo(EmploymentStatus.ACTIVE);
        assertThat(exits.findBySettlementId(settlementId).orElseThrow().getStatus())
                .isEqualTo(ExitStatus.WITHDRAWN);

        SagaState undoSaga = sagaService
                .findByKey(TerminationUndoSaga.NAME, "employee:" + employeeId).orElseThrow();
        assertThat(undoSaga.getStatus()).isEqualTo(SagaState.Status.RUNNING);

        sagaRunner.advanceOne(undoSaga.getId());

        assertThat(sagaService.findByKey(TerminationUndoSaga.NAME, "employee:" + employeeId)
                .orElseThrow().getStatus()).isEqualTo(SagaState.Status.COMPLETED);
        verify(authClient, times(1)).enableUser(eq(userId), any());
    }

    // ---------------------------------------------------------------- helpers

    private long persistActiveEmployeeWithUser(long userId) {
        Employee e = new Employee();
        e.setEmployeeCode("AUTH-" + System.nanoTime());
        e.setFullName("Auth Reversal Test");
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
