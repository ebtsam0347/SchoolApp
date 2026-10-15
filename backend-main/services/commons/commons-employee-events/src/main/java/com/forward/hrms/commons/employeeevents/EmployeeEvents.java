package com.forward.hrms.commons.employeeevents;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Map;

/**
 * Canonical employee.events contract. Producer (employee-service) and
 * every consumer (auth, attendance, payroll, chat) reference these
 * constants and records — the contract lives in one place so there is
 * no producer / consumer drift.
 *
 * <p>Service names and event-type strings are part of the wire
 * contract; renaming or removing one is a breaking change. {@link #TOPIC}
 * must be a compile-time constant — {@code @KafkaListener(topics = ...)}
 * rejects anything computed at class-init time, so we don't derive it
 * from {@code KafkaTopics.events("employee")}.
 */
public final class EmployeeEvents {

    public static final String SERVICE_NAME = "employee";
    public static final String TOPIC = "employee.events";

    public static final String EMPLOYEE_CREATED = "employee.created";
    public static final String EMPLOYEE_UPDATED = "employee.updated";
    public static final String EMPLOYEE_TERMINATED = "employee.terminated";
    public static final String EMPLOYEE_REINSTATED = "employee.reinstated";
    public static final String EMPLOYEE_RESIGNATION_SUBMITTED = "employee.resignation_submitted";
    public static final String EMPLOYEE_RESIGNATION_WITHDRAWN = "employee.resignation_withdrawn";
    public static final String EMPLOYEE_SUSPENDED = "employee.suspended";
    public static final String EMPLOYEE_SUSPENSION_LIFTED = "employee.suspension_lifted";
    public static final String EMPLOYEE_ESS_GRANTED = "employee.ess_granted";
    public static final String EMPLOYEE_ESS_REVOKED = "employee.ess_revoked";
    public static final String EMPLOYEE_MANAGER_CHANGED = "employee.manager_changed";
    public static final String EMPLOYEE_DEPARTMENT_CHANGED = "employee.department_changed";
    public static final String EMPLOYEE_SECTION_CHANGED = "employee.section_changed";
    public static final String EMPLOYEE_DESIGNATION_CHANGED = "employee.designation_changed";
    public static final String EMPLOYEE_GRADE_CHANGED = "employee.grade_changed";
    public static final String EMPLOYEE_CONFIRMED = "employee.confirmed";
    public static final String EMPLOYEE_PROBATION_EXTENDED = "employee.probation_extended";
    public static final String EMPLOYEE_DISCIPLINARY_ISSUED = "employee.disciplinary_action_issued";
    public static final String EMPLOYEE_DISCIPLINARY_DECIDED = "employee.disciplinary_action_decided";

    private EmployeeEvents() {}

    public record EmployeeCreated(
            long employeeId, Long userId, String employeeCode, String fullName,
            LocalDate joiningDate, Long departmentId, Long managerId, boolean hasEss) {}

    public record EmployeeUpdated(long employeeId, Long userId, Map<String, Object> changes) {}

    public record EmployeeTerminated(
            long employeeId, Long userId, String exitType,
            LocalDate lastWorkingDay, String reason) {}

    /**
     * Published when HR rejects a final settlement and
     * {@code TerminationSaga.undoTermination} restores the employee's
     * prior employment status, contracts, history, and suspensions.
     */
    public record EmployeeReinstated(
            long employeeId, Long userId, long settlementId,
            LocalDate lastWorkingDay, String reason) {}

    public record EmployeeResignationSubmitted(
            long employeeId, Long userId, LocalDate lastWorkingDay,
            LocalDate noticeStartDate, String reason) {}

    public record EmployeeResignationWithdrawn(long employeeId, Long userId, String reason) {}

    public record EmployeeSuspended(
            long employeeId, Long userId, long suspensionId,
            LocalDate startDate, LocalDate endDate, String reason, boolean withPay) {}

    public record EmployeeSuspensionLifted(
            long employeeId, Long userId, long suspensionId,
            LocalDate liftedOn, String reason) {}

    public record EmployeeEssGranted(long employeeId, long userId, LocalDateTime at) {}
    public record EmployeeEssRevoked(long employeeId, long userId, String by) {}

    public record EmployeeManagerChanged(
            long employeeId, Long oldManagerId, Long newManagerId, LocalDate effectiveAt) {}
    public record EmployeeDepartmentChanged(
            long employeeId, Long oldDepartmentId, Long newDepartmentId, LocalDate effectiveAt) {}
    public record EmployeeSectionChanged(
            long employeeId, Long oldSectionId, Long newSectionId, LocalDate effectiveAt) {}
    public record EmployeeDesignationChanged(
            long employeeId, Long oldDesignationId, Long newDesignationId, LocalDate effectiveAt) {}
    public record EmployeeGradeChanged(
            long employeeId, Long oldGradeId, Long newGradeId, LocalDate effectiveAt) {}

    public record EmployeeConfirmed(
            long employeeId, Long userId, LocalDate confirmationDate, Integer probationDays) {}
    public record EmployeeProbationExtended(
            long employeeId, int fromDays, int toDays, LocalDate newDueDate, String reason) {}

    public record DisciplinaryIssued(
            long actionId, long employeeId, String level, String category,
            LocalDate issuedOn, String reason) {}
    public record DisciplinaryDecided(
            long actionId, long employeeId, String level, String decision, LocalDate closedOn) {}
}
