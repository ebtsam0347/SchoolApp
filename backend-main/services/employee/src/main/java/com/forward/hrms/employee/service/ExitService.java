package com.forward.hrms.employee.service;

import com.forward.hrms.commons.batch.BatchOutcomes;
import com.forward.hrms.commons.batch.BatchRunner;
import com.forward.hrms.commons.core.error.ConflictError;
import com.forward.hrms.commons.core.error.NotFoundError;
import com.forward.hrms.commons.core.error.UnprocessableError;
import com.forward.hrms.commons.employeeevents.EmployeeEvents;
import com.forward.hrms.commons.kafka.outbox.AuditEmitter;
import com.forward.hrms.commons.kafka.outbox.Outbox;
import com.forward.hrms.employee.dto.request.AssignmentChangeRequest;
import com.forward.hrms.employee.dto.request.ResignRequest;
import com.forward.hrms.employee.dto.request.TerminateRequest;
import com.forward.hrms.employee.dto.request.WithdrawExitRequest;
import com.forward.hrms.employee.dto.response.ExitDto;
import com.forward.hrms.employee.dto.response.ExitDtoMapper;
import com.forward.hrms.employee.entity.Employee;
import com.forward.hrms.employee.entity.EmployeeExit;
import com.forward.hrms.employee.enums.EmployeeErrorCode;
import com.forward.hrms.employee.enums.EmploymentStatus;
import com.forward.hrms.employee.enums.ExitStatus;
import com.forward.hrms.employee.enums.ExitType;
import com.forward.hrms.employee.repository.EmployeeExitRepository;
import com.forward.hrms.employee.repository.EmployeeRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
public class ExitService {

    private final EmployeeRepository employees;
    private final EmployeeExitRepository exits;
    private final EmployeeService employeeService;
    private final TerminationSaga termination;
    private final Outbox outbox;
    private final AuditEmitter audit;
    private final EmployeeLifecycleMetrics lifecycle;
    private final BatchRunner batch;

    @Transactional(readOnly = true)
    public ExitDto get(long employeeId) {
        if (!employees.existsById(employeeId)) {
            throw new NotFoundError(EmployeeErrorCode.EMPLOYEE_NOT_FOUND, "employee not found");
        }
        return exits.findFirstByEmployeeIdOrderByIdDesc(employeeId)
                .map(ExitDtoMapper::of)
                .orElseThrow(() -> new NotFoundError(
                        EmployeeErrorCode.EXIT_NOT_FOUND, "no exit row for this employee"));
    }

    @Transactional
    public ExitDto resign(long employeeId, ResignRequest req) {
        Employee e = load(employeeId);
        LocalDate today = LocalDate.now();

        if (e.getEmploymentStatus() == EmploymentStatus.TERMINATED) {
            throw new ConflictError(EmployeeErrorCode.EMPLOYEE_TERMINATED, "employee already terminated");
        }
        if (req.lastWorkingDay() == null || !req.lastWorkingDay().isAfter(today)) {
            throw new UnprocessableError(EmployeeErrorCode.EXIT_LAST_WORKING_DAY_INVALID,
                    "lastWorkingDay must be in the future");
        }
        if (req.lastWorkingDay().isAfter(today.plusYears(1))) {
            throw new UnprocessableError(EmployeeErrorCode.LAST_WORKING_DAY_OUT_OF_RANGE,
                    "lastWorkingDay cannot be more than 1 year in the future");
        }
        if (exits.existsByEmployeeIdAndStatus(employeeId, ExitStatus.PENDING)) {
            throw new ConflictError(EmployeeErrorCode.EXIT_NOT_PENDING,
                    "employee already has a pending exit");
        }

        reassignDirectReports(e, req.reassignReportsTo());

        EmployeeExit exit = new EmployeeExit();
        exit.setEmployeeId(employeeId);
        exit.setExitType(ExitType.RESIGNATION);
        exit.setNoticeStartDate(today);
        exit.setLastWorkingDay(req.lastWorkingDay());
        exit.setReason(req.reason());
        exit.setNotes(req.notes());
        exit.setStatus(ExitStatus.PENDING);
        exits.save(exit);

        // SUSPENDED takes precedence — only flip ON_NOTICE if the employee is
        // currently ACTIVE. The doc: "the resignation clock continues running"
        // even while suspended.
        if (e.getEmploymentStatus() == EmploymentStatus.ACTIVE) {
            e.setEmploymentStatus(EmploymentStatus.ON_NOTICE);
            // Field-level status sync (employee.updated convention) so the
            // attendance nightly-close mirror tracks the transition.
            outbox.publish(EmployeeEvents.TOPIC, "employee:" + employeeId,
                    EmployeeEvents.EMPLOYEE_UPDATED,
                    new EmployeeEvents.EmployeeUpdated(employeeId, e.getUserId(),
                            Map.of("employmentStatus", EmploymentStatus.ON_NOTICE.name())));
        }

        outbox.publish(EmployeeEvents.TOPIC, "employee:" + employeeId,
                EmployeeEvents.EMPLOYEE_RESIGNATION_SUBMITTED,
                new EmployeeEvents.EmployeeResignationSubmitted(
                        employeeId, e.getUserId(), req.lastWorkingDay(),
                        today, req.reason()));
        audit.emit("employee", String.valueOf(employeeId), "resignation_submitted",
                null,
                Map.of(
                        "lastWorkingDay", req.lastWorkingDay().toString(),
                        "noticeStartDate", today.toString(),
                        "reason", req.reason() == null ? "" : req.reason()),
                true);
        lifecycle.resignSubmitted();
        return ExitDtoMapper.of(exit);
    }

    @Transactional
    public ExitDto terminate(long employeeId, TerminateRequest req) {
        Employee e = load(employeeId);
        LocalDate today = LocalDate.now();

        if (e.getEmploymentStatus() == EmploymentStatus.TERMINATED) {
            throw new ConflictError(EmployeeErrorCode.EMPLOYEE_TERMINATED, "employee already terminated");
        }

        reassignDirectReports(e, req.reassignReportsTo());

        // Manual terminate() behavior is unchanged: still finds-or-creates
        // against whatever the employee's current exit row is (which can
        // silently convert a PENDING resignation into a completed
        // termination, exactly as before V0621).
        EmployeeExit exit = exits.findFirstByEmployeeIdOrderByIdDesc(employeeId).orElseGet(EmployeeExit::new);
        exit.setEmployeeId(employeeId);
        exit.setExitType(ExitType.TERMINATION);
        exit.setNoticeStartDate(null);
        exit.setLastWorkingDay(today);
        exit.setReason(req.reason());
        exit.setNotes(req.notes());
        exit.setStatus(ExitStatus.PENDING); // terminationSaga flips to COMPLETED
        exit.setCompletedAt(null);
        exit.setWithdrawnAt(null);
        exit.setWithdrawnReason(null);
        exits.save(exit);

        termination.terminate(e, exit);
        lifecycle.terminated("manual");
        return ExitDtoMapper.of(exit);
    }

    @Transactional
    public ExitDto withdraw(long employeeId, WithdrawExitRequest req) {
        Employee e = load(employeeId);
        EmployeeExit exit = exits.findFirstByEmployeeIdOrderByIdDesc(employeeId)
                .orElseThrow(() -> new NotFoundError(
                        EmployeeErrorCode.EXIT_NOT_FOUND, "no exit row to withdraw"));
        if (exit.getStatus() != ExitStatus.PENDING) {
            throw new ConflictError(EmployeeErrorCode.EXIT_NOT_PENDING,
                    "only PENDING exits can be withdrawn");
        }
        exit.setStatus(ExitStatus.WITHDRAWN);
        exit.setWithdrawnAt(LocalDateTime.now());
        exit.setWithdrawnReason(req.reason());

        // SUSPENDED stays SUSPENDED; ON_NOTICE goes back to ACTIVE.
        if (e.getEmploymentStatus() == EmploymentStatus.ON_NOTICE) {
            e.setEmploymentStatus(EmploymentStatus.ACTIVE);
            // Field-level status sync (employee.updated convention) so the
            // attendance nightly-close mirror tracks the transition.
            outbox.publish(EmployeeEvents.TOPIC, "employee:" + employeeId,
                    EmployeeEvents.EMPLOYEE_UPDATED,
                    new EmployeeEvents.EmployeeUpdated(employeeId, e.getUserId(),
                            Map.of("employmentStatus", EmploymentStatus.ACTIVE.name())));
        }

        outbox.publish(EmployeeEvents.TOPIC, "employee:" + employeeId,
                EmployeeEvents.EMPLOYEE_RESIGNATION_WITHDRAWN,
                new EmployeeEvents.EmployeeResignationWithdrawn(
                        employeeId, e.getUserId(), req.reason()));
        audit.emit("employee", String.valueOf(employeeId), "resignation_withdrawn",
                null, Map.of("reason", req.reason()), true);
        return ExitDtoMapper.of(exit);
    }

    public int completeDuePending() {
        LocalDate today = LocalDate.now();
        List<EmployeeExit> due = exits.findAllByStatusAndLastWorkingDayLessThanEqual(
                ExitStatus.PENDING, today);
        BatchOutcomes<EmployeeExit, Void> outcomes = batch.runWithPartialVoid(due, exit -> {
            Employee e = employees.findById(exit.getEmployeeId()).orElse(null);
            if (e == null) return; // shouldn't happen — employee_id has FK
            termination.terminate(e, exit);
            lifecycle.terminated("notice_period_complete");
        });
        if (!outcomes.failed().isEmpty()) {
            for (var f : outcomes.failed()) {
                log.warn("completeDuePending: exit {} (employee {}) failed code={} msg={}",
                        f.item().getId(), f.item().getEmployeeId(), f.errorCode(), f.message(), f.cause());
            }
        }
        return outcomes.succeeded().size();
    }

    private void reassignDirectReports(Employee exiting, Long reassignReportsTo) {
        List<Employee> reports = employees.findAllByManagerId(exiting.getId());
        if (reports.isEmpty()) return;
        if (reassignReportsTo == null) {
            throw new ConflictError(EmployeeErrorCode.EMPLOYEE_HAS_ACTIVE_REPORTS,
                    "employee has direct reports — pass reassignReportsTo to continue");
        }
        if (reassignReportsTo.equals(exiting.getId())) {
            throw new ConflictError(EmployeeErrorCode.EMPLOYEE_HAS_ACTIVE_REPORTS,
                    "reassignReportsTo cannot be the exiting employee");
        }
        if (!employees.existsById(reassignReportsTo)) {
            throw new NotFoundError(EmployeeErrorCode.MANAGER_NOT_FOUND,
                    "reassignReportsTo employee not found");
        }
        AssignmentChangeRequest move = new AssignmentChangeRequest(
                reassignReportsTo, "auto-reassign on exit of " + exiting.getEmployeeCode());
        for (Employee report : reports) {
            employeeService.changeManager(report.getId(), move);
        }
    }

    private Employee load(long id) {
        return employees.findById(id)
                .orElseThrow(() -> new NotFoundError(
                        EmployeeErrorCode.EMPLOYEE_NOT_FOUND, "employee not found"));
    }

    @Transactional
    public void finalizeTermination(
            long employeeId,
            long settlementId,
            LocalDate lastWorkingDay) {

        Employee employee = load(employeeId);

        /*
         * --------------------------------------------------------
         * Exit Record — correlated by settlementId, not employeeId
         * --------------------------------------------------------
         *
         * employee_exits is historical (V0621): an employee can have
         * more than one row over their lifetime, so the exit this event
         * must act on is the one settlementId names, never merely "the
         * employee's exit." A late/duplicate PAID event for settlement
         * S1 must not accidentally complete or re-read the exit that
         * belongs to a different settlement S2 for the same employee.
         */
        EmployeeExit exit = exits.findBySettlementId(settlementId)
                .orElseThrow(() ->
                        new IllegalStateException(
                                "Settlement " + settlementId +
                                        " has no EmployeeExit to finalize."
                        ));

        /*
         * --------------------------------------------------------
         * Safety Validation
         * --------------------------------------------------------
         *
         * Extra protection against accidental mismatch.
         */
        if (!exit.getEmployeeId().equals(employeeId)) {

            throw new IllegalStateException(
                    "Settlement " + settlementId +
                            " belongs to employee " + exit.getEmployeeId() +
                            " but payroll event contains employee " + employeeId
            );
        }

        /*
         * --------------------------------------------------------
         * Idempotency
         * --------------------------------------------------------
         *
         * Kafka may redeliver the same event. If this settlement's
         * exit is already completed, simply ignore the duplicate.
         */
        if (exit.getStatus() == ExitStatus.COMPLETED) {

            log.info(
                    "Employee {} exit for settlement {} already completed. Ignoring duplicate payroll settlement event.",
                    employeeId,
                    settlementId
            );

            return;
        }

        /*
         * --------------------------------------------------------
         * Payroll is the source of truth for the
         * actual final working day once settlement
         * has been paid.
         * --------------------------------------------------------
         */
        exit.setLastWorkingDay(lastWorkingDay);

        /*
         * --------------------------------------------------------
         * Delegate business logic to TerminationSaga
         * --------------------------------------------------------
         */
        termination.completeTermination(employee, exit);

        /*
         * --------------------------------------------------------
         * Metrics
         * --------------------------------------------------------
         */
        lifecycle.terminated("final_settlement_paid");

        log.info(
                "Employee {} successfully terminated after settlement {} payment.",
                employeeId,
                settlementId
        );

    }


    /**
     * Creates the employee exists record with pending state, very useful & important method.
     */
    @Transactional
    public void createPendingExit(

            long settlementId,

            long employeeId,

            LocalDate lastWorkingDay,

            String terminationReason
    ) {

        Employee employee = load(employeeId);
        log.info("Employee loaded in create pending exit method code : {}", employee.getId());

        log.info(
                "createPendingExit(settlementId={}, employeeId={}, lastWorkingDay={}, terminationReason={} reached in create pending exit method code)",
                settlementId,
                employeeId,
                lastWorkingDay,
                terminationReason
        );
        /*
         * --------------------------------------------------------
         * Idempotency — keyed by settlementId, not employeeId
         * --------------------------------------------------------
         *
         * Kafka may redeliver the same settlement. employee_exits is
         * historical (V0621): the same employee can legitimately have
         * an earlier WITHDRAWN exit from a rejected/canceled
         * settlement, so existence "for this employee" is not a valid
         * duplicate signal anymore — only "for this exact settlement"
         * is. A late/duplicate event for one settlement must never
         * operate on a different settlement's exit row for the same
         * employee, and a genuinely new settlement (after a proper
         * reject/cancel reversal) must always get its own new row.
         */
        if (exits.existsBySettlementId(settlementId)) {

            log.info(
                    "Pending Exit already exists for settlement {}.",
                    settlementId
            );

            return;
        }

        EmployeeExit exit = new EmployeeExit();

        exit.setSettlementId(settlementId);

        exit.setEmployeeId(employeeId);

        exit.setExitType(ExitType.TERMINATION);

        exit.setNoticeStartDate(null);

        exit.setLastWorkingDay(lastWorkingDay);

        exit.setReason(terminationReason);

        // Captured so undoTermination (HR rejects the settlement) can
        // restore the exact prior state instead of assuming ACTIVE.
        exit.setPreviousEmploymentStatus(employee.getEmploymentStatus().name());

        exit.setNotes("Created automatically from Payroll Final Settlement #" + settlementId);

        exit.setStatus(ExitStatus.PENDING);

        exit.setCompletedAt(null);

        exit.setWithdrawnAt(null);

        exit.setWithdrawnReason(null);

        exits.save(exit);

        log.info("Exit saved successfully. Exit id={}", exit.getId());

        /*
         * --------------------------------------------------------
         * Access revocation now happens at settlement CREATION,
         * not at settlement PAID.
         * --------------------------------------------------------
         *
         * TerminationSaga.completeTermination flips employment status
         * to TERMINATED, closes history/contracts, marks the exit
         * COMPLETED, publishes employee.terminated, and starts the
         * TERMINATION_DISABLE_AUTH saga (disables ESS/auth access).
         *
         * finalizeTermination (fired later by payroll.final_settlement.paid)
         * is idempotent and will simply no-op once this has run.
         */
        termination.completeTermination(employee, exit);
        lifecycle.terminated("final_settlement_created");

        audit.emit(

                "employee",

                String.valueOf(employeeId),

                "pending_exit_created",

                null,
                Map.of(
                        "settlementId", settlementId,
                        "employeeId", employeeId,
                        "lastWorkingDay", lastWorkingDay,
                        "status", exit.getStatus().name(),
                        "exitType", ExitType.TERMINATION.name()
                ),
                true,
                "Exit created and employee terminated from Payroll Final Settlement."
        );

        log.info(
                "Exit created and employee {} terminated for settlement {}.",
                employeeId,
                settlementId
        );
    }


    /**
     * HR rejected a DRAFT final settlement, or canceled a DRAFT/
     * HR_APPROVED one — both mean the same thing from this service's
     * point of view: payroll has already reversed its own financial
     * closing and published {@code payroll.final_settlement.rejected}
     * or {@code payroll.final_settlement.cancelled}, so undo everything
     * this service closed at settlement creation. {@code triggerVerb}
     * ("rejected" / "canceled") only affects audit/metric wording.
     */
    @Transactional
    public void undoTermination(
            long employeeId,
            long settlementId,
            String rejectionReason,
            String triggerVerb
    ) {

        Employee employee = load(employeeId);

        /*
         * --------------------------------------------------------
         * Exit Record
         * --------------------------------------------------------
         *
         * Every Final Settlement creates exactly one EmployeeExit
         * record — correlate using settlementId, not employeeId.
         */
        EmployeeExit exit = exits.findBySettlementId(settlementId)
                .orElseThrow(() ->
                        new IllegalStateException(
                                "Settlement " + settlementId +
                                        " has no EmployeeExit to undo."
                        ));

        /*
         * --------------------------------------------------------
         * Safety Validation
         * --------------------------------------------------------
         *
         * Extra protection against accidental mismatch.
         */
        if (!exit.getEmployeeId().equals(employeeId)) {

            throw new IllegalStateException(
                    "Settlement " + settlementId +
                            " belongs to employee " + exit.getEmployeeId() +
                            " but payroll rejection event contains employee " + employeeId
            );
        }

        /*
         * --------------------------------------------------------
         * Idempotency
         * --------------------------------------------------------
         *
         * Kafka may redeliver. If already withdrawn, ignore.
         */
        if (exit.getStatus() == ExitStatus.WITHDRAWN) {

            log.info(
                    "Employee {} exit for settlement {} already withdrawn. Ignoring duplicate rejection event.",
                    employeeId,
                    settlementId
            );

            return;
        }

        if (exit.getStatus() != ExitStatus.COMPLETED) {

            throw new IllegalStateException(
                    "Cannot undo termination for settlement " + settlementId +
                            " because Exit status is " + exit.getStatus() + "."
            );
        }

        /*
         * --------------------------------------------------------
         * Delegate business logic to TerminationSaga
         * --------------------------------------------------------
         */
        termination.undoTermination(employee, exit, rejectionReason, triggerVerb);

        /*
         * --------------------------------------------------------
         * Metrics
         * --------------------------------------------------------
         */
        lifecycle.reinstated("final_settlement_" + triggerVerb);

        log.info(
                "Employee {} termination undone after settlement {} rejection.",
                employeeId,
                settlementId
        );
    }
}
