package com.forward.hrms.employee.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.forward.hrms.commons.employeeevents.EmployeeEvents;
import com.forward.hrms.commons.kafka.outbox.AuditEmitter;
import com.forward.hrms.commons.kafka.outbox.Outbox;
import com.forward.hrms.commons.saga.SagaContext;
import com.forward.hrms.commons.saga.SagaService;
import com.forward.hrms.employee.client.AuthClient;
import com.forward.hrms.employee.entity.Employee;
import com.forward.hrms.employee.entity.EmployeeExit;
import com.forward.hrms.employee.entity.EmployeeSuspension;
import com.forward.hrms.employee.entity.EmploymentContract;
import com.forward.hrms.employee.enums.ContractStatus;
import com.forward.hrms.employee.enums.EmploymentStatus;
import com.forward.hrms.employee.enums.ExitStatus;
import com.forward.hrms.employee.enums.SuspensionStatus;
import com.forward.hrms.employee.repository.EmployeeSuspensionRepository;
import com.forward.hrms.employee.repository.EmploymentContractRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

@Component
@Slf4j
public class TerminationSaga {

    private final EmployeeSuspensionRepository suspensions;
    private final EmployeeHistoryService history;
    private final Outbox outbox;
    private final AuditEmitter audit;
    private final SagaService sagaService;
    private final ObjectMapper sagaJson;
    private final EmploymentContractRepository contractsRepository;

    public TerminationSaga(EmployeeSuspensionRepository suspensions,
                           EmployeeHistoryService history,
                           Outbox outbox,
                           AuditEmitter audit,
                           SagaService sagaService,
                           @Qualifier("sagaObjectMapper") ObjectMapper sagaJson,
                           EmploymentContractRepository contractsRepository) {
        this.suspensions = suspensions;
        this.history = history;
        this.outbox = outbox;
        this.audit = audit;
        this.sagaService = sagaService;
        this.sagaJson = sagaJson;
        this.contractsRepository = contractsRepository;
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public void terminate(Employee e, EmployeeExit exit) {

        e.setEmploymentStatus(EmploymentStatus.TERMINATED);

        // Auto-lift any active suspension so history is consistent.
        suspensions.findFirstByEmployeeIdAndStatus(e.getId(), SuspensionStatus.ACTIVE)
                .ifPresent(s -> liftAtTermination(s, exit.getLastWorkingDay()));

        history.closeOnExit(e, exit.getLastWorkingDay());

        exit.setStatus(ExitStatus.COMPLETED);
        exit.setCompletedAt(LocalDateTime.now());

        outbox.publish(EmployeeEvents.TOPIC, "employee:" + e.getId(),
                EmployeeEvents.EMPLOYEE_TERMINATED,
                new EmployeeEvents.EmployeeTerminated(
                        e.getId(), e.getUserId(), exit.getExitType().name(),
                        exit.getLastWorkingDay(), exit.getReason()));
        audit.emit("employee", String.valueOf(e.getId()), "terminated",
                null,
                Map.of(
                        "exitType", exit.getExitType().name(),
                        "lastWorkingDay", exit.getLastWorkingDay().toString(),
                        "reason", exit.getReason() == null ? "" : exit.getReason()),
                true);

        if (e.getUserId() != null) {
            startDisableAuthUserSaga(e);
        }
    }

    private void startDisableAuthUserSaga(Employee e) {
        ObjectNode payload = sagaJson.createObjectNode();
        payload.put("employeeId", e.getId());
        payload.put("userId", e.getUserId());
        SagaContext.writeIdentity(payload, AuthClient.currentRequestIdentity());
        sagaService.start(TerminationDisableSaga.NAME, "employee:" + e.getId(), payload);
    }

    private void startEnableAuthUserSaga(Employee e) {
        ObjectNode payload = sagaJson.createObjectNode();
        payload.put("employeeId", e.getId());
        payload.put("userId", e.getUserId());
        SagaContext.writeIdentity(payload, AuthClient.currentRequestIdentity());
        sagaService.start(TerminationUndoSaga.NAME, "employee:" + e.getId(), payload);
    }

    private static void liftAtTermination(EmployeeSuspension s, java.time.LocalDate liftedOn) {
        s.setStatus(SuspensionStatus.LIFTED);
        s.setLiftedOn(liftedOn);
        s.setLiftedReason("Terminated");
    }
    @Transactional(propagation = Propagation.MANDATORY)
    public void completeTermination(Employee employee, EmployeeExit exit) {

        /*
         * --------------------------------------------------------
         * Idempotency
         * --------------------------------------------------------
         *
         * Kafka may redeliver the same event.
         * If already completed simply ignore.
         */

        if (exit.getStatus() == ExitStatus.COMPLETED) {

            log.info(
                    "Employee {} termination already completed.",
                    employee.getId()
            );

            return;
        }

        if (exit.getStatus() != ExitStatus.PENDING) {

            throw new IllegalStateException(
                    "Cannot complete termination because Exit status is "
                            + exit.getStatus()
                            + "."
            );
        }

        /*
         * --------------------------------------------------------
         * Employee Status
         * --------------------------------------------------------
         */
        employee.setEmploymentStatus(EmploymentStatus.TERMINATED);


        /*
         * --------------------------------------------------------
         * Auto lift suspension
         * --------------------------------------------------------
         */
        suspensions.findFirstByEmployeeIdAndStatus(
                        employee.getId(),
                        SuspensionStatus.ACTIVE)
                .ifPresent(s -> liftAtTermination(
                        s,
                        exit.getLastWorkingDay()
                ));

        /*
         * --------------------------------------------------------
         * Close employee history
         * --------------------------------------------------------
         */
        history.closeOnExit(
                employee,
                exit.getLastWorkingDay()
        );

        /*
         * --------------------------------------------------------
         * Complete exit
         * --------------------------------------------------------
         */
        exit.setStatus(ExitStatus.COMPLETED);
        exit.setCompletedAt(LocalDateTime.now());

        /*
         * --------------------------------------------------------
         * Employment Contracts Closing
         * --------------------------------------------------------
         */
        employeeActiveContractsClosed(
                employee,
                exit.getLastWorkingDay()
        );


        /*
         * --------------------------------------------------------
         * Publish employee terminated event
         * --------------------------------------------------------
         */
        outbox.publish(
                EmployeeEvents.TOPIC,
                "employee:" + employee.getId(),
                EmployeeEvents.EMPLOYEE_TERMINATED,
                new EmployeeEvents.EmployeeTerminated(
                        employee.getId(),
                        employee.getUserId(),
                        exit.getExitType().name(),
                        exit.getLastWorkingDay(),
                        exit.getReason()
                )
        );

        /*
         * --------------------------------------------------------
         * Audit
         * --------------------------------------------------------
         */
        audit.emit(
                "employee",
                String.valueOf(employee.getId()),
                "terminated_after_final_settlement",
                null,
                Map.of(
                        "exitType", exit.getExitType().name(),
                        "lastWorkingDay", exit.getLastWorkingDay().toString(),
                        "reason", exit.getReason() == null ? "" : exit.getReason()
                ),
                true
        );

        /*
         * --------------------------------------------------------
         * Disable ESS / Auth
         * --------------------------------------------------------
         */
        if (employee.getUserId() != null) {
            startDisableAuthUserSaga(employee);
        }

        log.info(
                "Employee {} termination completed from settlement {}.",
                employee.getId(),
                exit.getSettlementId()
        );
    }


    /**
     * Reverses {@link #completeTermination}. Fired when HR rejects a
     * DRAFT final settlement, or cancels a DRAFT/HR_APPROVED one —
     * restores employment status, reopens employment contracts and
     * history closed at settlement creation, restores auto-lifted
     * suspensions, marks the exit WITHDRAWN, and re-enables ESS/auth if
     * it was revoked. {@code triggerVerb} ("rejected" / "canceled")
     * only affects the withdrawn-reason text and audit action name —
     * the side effects reversed are identical either way.
     */
    @Transactional(propagation = Propagation.MANDATORY)
    public void undoTermination(Employee employee, EmployeeExit exit, String rejectionReason, String triggerVerb) {

        /*
         * --------------------------------------------------------
         * Idempotency
         * --------------------------------------------------------
         *
         * Kafka may redeliver the same event.
         */
        if (exit.getStatus() == ExitStatus.WITHDRAWN) {

            log.info(
                    "Employee {} termination already undone for exit {}.",
                    employee.getId(),
                    exit.getId()
            );

            return;
        }

        if (exit.getStatus() != ExitStatus.COMPLETED) {

            throw new IllegalStateException(
                    "Cannot undo termination because Exit status is "
                            + exit.getStatus()
                            + "."
            );
        }

        /*
         * --------------------------------------------------------
         * Employee Status
         * --------------------------------------------------------
         *
         * Restore the exact prior employment status when we captured
         * it; default to ACTIVE for older exits created before this
         * column existed.
         */
        EmploymentStatus restoredStatus = EmploymentStatus.ACTIVE;
        if (exit.getPreviousEmploymentStatus() != null) {
            try {
                restoredStatus = EmploymentStatus.valueOf(exit.getPreviousEmploymentStatus());
            } catch (IllegalArgumentException ex) {
                log.warn("Unknown previousEmploymentStatus '{}' on exit {}; defaulting to ACTIVE.",
                        exit.getPreviousEmploymentStatus(), exit.getId());
            }
        }
        employee.setEmploymentStatus(restoredStatus);

        /*
         * --------------------------------------------------------
         * Restore auto-lifted suspension
         * --------------------------------------------------------
         */
        suspensions.findAllByEmployeeIdAndStatusAndLiftedOn(
                        employee.getId(), SuspensionStatus.LIFTED, exit.getLastWorkingDay())
                .stream()
                .filter(s -> "Terminated".equals(s.getLiftedReason()))
                .forEach(s -> {
                    s.setStatus(SuspensionStatus.ACTIVE);
                    s.setLiftedOn(null);
                    s.setLiftedReason(null);
                });

        /*
         * --------------------------------------------------------
         * Reopen employee history
         * --------------------------------------------------------
         */
        history.reopenOnUndo(employee, exit.getLastWorkingDay());

        /*
         * --------------------------------------------------------
         * Reopen employment contracts
         * --------------------------------------------------------
         */
        employeeContractsReopened(employee, exit.getLastWorkingDay());

        /*
         * --------------------------------------------------------
         * Withdraw exit
         * --------------------------------------------------------
         */
        exit.setStatus(ExitStatus.WITHDRAWN);
        exit.setWithdrawnAt(LocalDateTime.now());
        exit.setWithdrawnReason("Final settlement " + triggerVerb + " by HR"
                + (rejectionReason == null || rejectionReason.isBlank() ? "" : ": " + rejectionReason));

        /*
         * --------------------------------------------------------
         * Publish employee reinstated event
         * --------------------------------------------------------
         */
        outbox.publish(
                EmployeeEvents.TOPIC,
                "employee:" + employee.getId(),
                EmployeeEvents.EMPLOYEE_REINSTATED,
                new EmployeeEvents.EmployeeReinstated(
                        employee.getId(),
                        employee.getUserId(),
                        exit.getSettlementId() == null ? 0L : exit.getSettlementId(),
                        exit.getLastWorkingDay(),
                        rejectionReason
                )
        );

        /*
         * --------------------------------------------------------
         * Audit
         * --------------------------------------------------------
         */
        audit.emit(
                "employee",
                String.valueOf(employee.getId()),
                "reinstated_after_settlement_" + triggerVerb,
                null,
                Map.of(
                        "settlementId", exit.getSettlementId() == null ? 0L : exit.getSettlementId(),
                        "lastWorkingDay", exit.getLastWorkingDay().toString(),
                        "restoredStatus", restoredStatus.name(),
                        "reason", rejectionReason == null ? "" : rejectionReason
                ),
                true
        );

        /*
         * --------------------------------------------------------
         * Re-enable ESS / Auth
         * --------------------------------------------------------
         */
        if (employee.getUserId() != null) {
            startEnableAuthUserSaga(employee);
        }

        log.info(
                "Employee {} termination undone for settlement {}.",
                employee.getId(),
                exit.getSettlementId()
        );
    }


    /*
     * --------------------------------------------------------
     * Helping methods
     * --------------------------------------------------------
     */

    private void employeeContractsReopened(
            Employee employee,
            LocalDate lastWorkingDay
    ) {

        List<EmploymentContract> closedContracts =
                contractsRepository.findAllByEmployeeIdAndStatusAndEndDate(
                        employee.getId(),
                        ContractStatus.TERMINATED,
                        lastWorkingDay
                );

        if (closedContracts.isEmpty()) {

            log.debug(
                    "No terminated employment contracts found to reopen for employee {}.",
                    employee.getId()
            );

            return;
        }

        LocalDateTime now = LocalDateTime.now();
        for (EmploymentContract contract : closedContracts) {

            contract.setStatus(ContractStatus.ACTIVE);
            contract.setEndDate(null);
            contract.setUpdatedAt(now);
            // No save() required because we're inside a @Transactional method.
        }

        log.info(
                "{} employment contract(s) reopened for employee {}.",
                closedContracts.size(),
                employee.getId()
        );
    }

    private void employeeActiveContractsClosed(
            Employee employee,
            LocalDate lastWorkingDay
    ) {

        List<EmploymentContract> activeContracts =
                contractsRepository.findAllByEmployeeIdAndStatus(
                        employee.getId(),
                        ContractStatus.ACTIVE
                );

        if (activeContracts.isEmpty()) {

            log.debug(
                    "No active employment contracts found for employee {}.",
                    employee.getId()
            );

            return;
        }

        LocalDateTime now = LocalDateTime.now();
        for (EmploymentContract contract : activeContracts) {

            contract.setStatus(ContractStatus.TERMINATED);
            contract.setEndDate(lastWorkingDay);
            contract.setExtended(false);
            contract.setUpdatedAt(now);
            // No save() required because we're inside a @Transactional method.
        }

        log.info(
                "{} employment contract(s) terminated for employee {}.",
                activeContracts.size(),
                employee.getId()
        );
    }
}
