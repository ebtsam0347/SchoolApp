package com.forward.hrms.employee.integration;

import com.forward.hrms.commons.core.error.ConflictError;
import com.forward.hrms.commons.testsupport.containers.WithTestcontainers;
import com.forward.hrms.employee.client.AuthClient;
import com.forward.hrms.employee.dto.request.ResignRequest;
import com.forward.hrms.employee.dto.request.TerminateRequest;
import com.forward.hrms.employee.dto.request.WithdrawExitRequest;
import com.forward.hrms.employee.entity.Employee;
import com.forward.hrms.employee.entity.EmployeeExit;
import com.forward.hrms.employee.enums.EmploymentStatus;
import com.forward.hrms.employee.enums.ExitStatus;
import com.forward.hrms.employee.enums.ExitType;
import com.forward.hrms.employee.repository.EmployeeExitRepository;
import com.forward.hrms.employee.repository.EmployeeRepository;
import com.forward.hrms.employee.service.ExitService;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doNothing;

/**
 * Covers the audited employee_exits defects directly against a real
 * Postgres (via Testcontainers): the historical exit model (V0621),
 * settlementId-keyed idempotency in ExitService, resign() no longer
 * hitting the (now-removed) employee_id UNIQUE constraint, and manual
 * terminate() staying non-withdrawable.
 *
 * <p>Drives ExitService directly rather than through Kafka — the
 * payroll.final_settlement.* payloads are what PayrollEventListener
 * would hand to ExitService after parsing; testing at this boundary
 * covers the same logic without needing a live payroll-service event.
 */
@SpringBootTest
@WithTestcontainers
class ExitHistoricalLifecycleIntegrationTest {

    @Autowired ExitService exitService;
    @Autowired EmployeeRepository employees;
    @Autowired EmployeeExitRepository exits;

    @MockitoBean AuthClient authClient;

    @BeforeEach
    void mockAuth() {
        doNothing().when(authClient).disableUser(anyLong(), any(), anyString());
        doNothing().when(authClient).enableUser(anyLong(), any());
    }

    // ------------------------------------------------------------ N

    @Test
    void resignWithdrawResignAgain_succeedsAndKeepsBothHistoricalRows() {
        long employeeId = persistActiveEmployee();

        exitService.resign(employeeId, new ResignRequest(
                LocalDate.now().plusDays(30), "personal", null, null));
        List<EmployeeExit> afterFirstResign = exits.findAll().stream()
                .filter(e -> e.getEmployeeId().equals(employeeId)).toList();
        assertThat(afterFirstResign).hasSize(1);
        long firstExitId = afterFirstResign.get(0).getId();

        exitService.withdraw(employeeId, new WithdrawExitRequest("changed my mind"));
        EmployeeExit firstAfterWithdraw = exits.findById(firstExitId).orElseThrow();
        assertThat(firstAfterWithdraw.getStatus()).isEqualTo(ExitStatus.WITHDRAWN);
        assertThat(employees.findById(employeeId).orElseThrow().getEmploymentStatus())
                .isEqualTo(EmploymentStatus.ACTIVE);

        // Second resignation must succeed — pre-V0621 this violated
        // employee_exits.employee_id UNIQUE.
        exitService.resign(employeeId, new ResignRequest(
                LocalDate.now().plusDays(45), "personal again", null, null));

        List<EmployeeExit> all = exits.findAll().stream()
                .filter(e -> e.getEmployeeId().equals(employeeId))
                .sorted((a, b) -> Long.compare(a.getId(), b.getId()))
                .toList();
        assertThat(all).hasSize(2);
        assertThat(all.get(0).getId()).isEqualTo(firstExitId);
        assertThat(all.get(0).getStatus()).isEqualTo(ExitStatus.WITHDRAWN);
        assertThat(all.get(1).getStatus()).isEqualTo(ExitStatus.PENDING);
        assertThat(all.get(1).getId()).isNotEqualTo(firstExitId);
    }

    @Test
    void secondResignWhileFirstStillPending_isRejected() {
        long employeeId = persistActiveEmployee();
        exitService.resign(employeeId, new ResignRequest(
                LocalDate.now().plusDays(30), "personal", null, null));

        assertThatThrownBy(() -> exitService.resign(employeeId, new ResignRequest(
                LocalDate.now().plusDays(60), "again", null, null)))
                .isInstanceOf(ConflictError.class);
    }

    // ------------------------------------------------------------ O

    @Test
    void manualTerminate_remainsNonWithdrawable() {
        long employeeId = persistActiveEmployee();

        exitService.terminate(employeeId, new TerminateRequest("policy violation", null, null));

        assertThat(employees.findById(employeeId).orElseThrow().getEmploymentStatus())
                .isEqualTo(EmploymentStatus.TERMINATED);

        assertThatThrownBy(() -> exitService.withdraw(employeeId, new WithdrawExitRequest("no")))
                .isInstanceOf(ConflictError.class);
    }

    // ------------------------------------------------------------ B/C/D employee-side + E/F re-linking

    @Test
    void createPendingExit_thenUndo_thenCreateAgain_getsFreshRowAndReTerminates() {
        long employeeId = persistActiveEmployee();
        long settlementOne = uniqueSettlementId();

        exitService.createPendingExit(settlementOne, employeeId, LocalDate.now(), "termination settlement");

        Employee afterFirst = employees.findById(employeeId).orElseThrow();
        assertThat(afterFirst.getEmploymentStatus()).isEqualTo(EmploymentStatus.TERMINATED);
        EmployeeExit exitOne = exits.findBySettlementId(settlementOne).orElseThrow();
        assertThat(exitOne.getStatus()).isEqualTo(ExitStatus.COMPLETED);
        assertThat(exitOne.getExitType()).isEqualTo(ExitType.TERMINATION);

        // Reject (or cancel — undoTermination is shared) — employee-side reversal.
        exitService.undoTermination(employeeId, settlementOne, "wrong employee", "rejected");

        Employee afterUndo = employees.findById(employeeId).orElseThrow();
        assertThat(afterUndo.getEmploymentStatus()).isEqualTo(EmploymentStatus.ACTIVE);
        EmployeeExit exitOneAfterUndo = exits.findBySettlementId(settlementOne).orElseThrow();
        assertThat(exitOneAfterUndo.getStatus()).isEqualTo(ExitStatus.WITHDRAWN);

        // Recreate for a second settlement — must get its OWN new row, not
        // silently no-op against the (now WITHDRAWN) first exit. This is
        // the exact defect from the audit: createPendingExit used to check
        // "does any exit exist for this employee" instead of "for this
        // settlement".
        long settlementTwo = uniqueSettlementId();
        exitService.createPendingExit(settlementTwo, employeeId, LocalDate.now().plusDays(1), "termination settlement 2");

        Employee afterSecond = employees.findById(employeeId).orElseThrow();
        assertThat(afterSecond.getEmploymentStatus()).isEqualTo(EmploymentStatus.TERMINATED);

        EmployeeExit exitTwo = exits.findBySettlementId(settlementTwo).orElseThrow();
        assertThat(exitTwo.getId()).isNotEqualTo(exitOne.getId());
        assertThat(exitTwo.getStatus()).isEqualTo(ExitStatus.COMPLETED);

        // The first (withdrawn) exit must be untouched by the second settlement.
        EmployeeExit exitOneStillUntouched = exits.findBySettlementId(settlementOne).orElseThrow();
        assertThat(exitOneStillUntouched.getStatus()).isEqualTo(ExitStatus.WITHDRAWN);
        assertThat(exitOneStillUntouched.getId()).isEqualTo(exitOne.getId());

        // finalizeTermination (PAID) for the second settlement must act on
        // exitTwo, never fall back to a different settlement's row.
        exitService.finalizeTermination(employeeId, settlementTwo, LocalDate.now().plusDays(1));
        EmployeeExit exitTwoAfterPaid = exits.findBySettlementId(settlementTwo).orElseThrow();
        assertThat(exitTwoAfterPaid.getStatus()).isEqualTo(ExitStatus.COMPLETED);
    }

    // ------------------------------------------------------------ J

    @Test
    void duplicateCreatedEvent_isNoOp() {
        long employeeId = persistActiveEmployee();
        long settlementId = uniqueSettlementId();

        exitService.createPendingExit(settlementId, employeeId, LocalDate.now(), "reason");
        exitService.createPendingExit(settlementId, employeeId, LocalDate.now(), "reason"); // duplicate

        List<EmployeeExit> rows = exits.findAll().stream()
                .filter(e -> e.getEmployeeId().equals(employeeId)).toList();
        assertThat(rows).hasSize(1);
    }

    // ------------------------------------------------------------ K

    @Test
    void duplicateRejectedEvent_isNoOp() {
        long employeeId = persistActiveEmployee();
        long settlementId = uniqueSettlementId();
        exitService.createPendingExit(settlementId, employeeId, LocalDate.now(), "reason");

        exitService.undoTermination(employeeId, settlementId, "reason", "rejected");
        // Duplicate delivery must not throw and must not re-flip anything.
        exitService.undoTermination(employeeId, settlementId, "reason", "rejected");

        Employee employee = employees.findById(employeeId).orElseThrow();
        assertThat(employee.getEmploymentStatus()).isEqualTo(EmploymentStatus.ACTIVE);
        assertThat(exits.findBySettlementId(settlementId).orElseThrow().getStatus())
                .isEqualTo(ExitStatus.WITHDRAWN);
    }

    // ------------------------------------------------------------ L

    @Test
    void duplicateCancelledEvent_isNoOp() {
        long employeeId = persistActiveEmployee();
        long settlementId = uniqueSettlementId();
        exitService.createPendingExit(settlementId, employeeId, LocalDate.now(), "reason");

        exitService.undoTermination(employeeId, settlementId, "cancelled by HR", "cancelled");
        exitService.undoTermination(employeeId, settlementId, "cancelled by HR", "cancelled");

        assertThat(employees.findById(employeeId).orElseThrow().getEmploymentStatus())
                .isEqualTo(EmploymentStatus.ACTIVE);
        assertThat(exits.findBySettlementId(settlementId).orElseThrow().getStatus())
                .isEqualTo(ExitStatus.WITHDRAWN);
    }

    // ------------------------------------------------------------ M

    @Test
    void duplicatePaidEvent_isNoOp() {
        long employeeId = persistActiveEmployee();
        long settlementId = uniqueSettlementId();
        exitService.createPendingExit(settlementId, employeeId, LocalDate.now(), "reason");

        exitService.finalizeTermination(employeeId, settlementId, LocalDate.now());
        exitService.finalizeTermination(employeeId, settlementId, LocalDate.now()); // duplicate

        assertThat(employees.findById(employeeId).orElseThrow().getEmploymentStatus())
                .isEqualTo(EmploymentStatus.TERMINATED);
        assertThat(exits.findBySettlementId(settlementId).orElseThrow().getStatus())
                .isEqualTo(ExitStatus.COMPLETED);
    }

    // ---------------------------------------------------------------- helpers

    private long persistActiveEmployee() {
        Employee e = new Employee();
        e.setEmployeeCode("EXT-" + System.nanoTime());
        e.setFullName("Exit Lifecycle Test");
        e.setJoiningDate(LocalDate.now().minusYears(1));
        e.setEmploymentStatus(EmploymentStatus.ACTIVE);
        return employees.save(e).getId();
    }

    private long uniqueSettlementId() {
        return System.nanoTime() & 0x7fffffffffL;
    }
}
