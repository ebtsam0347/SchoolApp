package com.forward.hrms.payroll.events;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.forward.hrms.commons.employeeevents.EmployeeEvents;
import com.forward.hrms.commons.employeeevents.EmployeeEventsConsumerSupport;
import com.forward.hrms.payroll.service.EmployeePayrollSettingsService;
import com.forward.hrms.payroll.service.EmployeeSalaryComponentService;
import com.forward.hrms.payroll.service.EmployeeSalaryService;

import lombok.extern.slf4j.Slf4j;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;

/**
 * Reacts to employee-service events. Idempotent because Kafka may
 * redeliver:
 * <ul>
 *   <li>{@code employee.created} → seed default {@code employee_payroll_settings}.
 *       Salary is NOT auto-seeded — the onboarding saga's
 *       {@code SET_BASIC_SALARY} step writes the first row when the
 *       payload includes salary, otherwise HR sets it via
 *       {@code POST /salary/employees/{id}} later. Auto-seeding a
 *       zero-basic placeholder used to race the saga step on the same
 *       {@code effective_from = joiningDate} and made the same-day
 *       manual save fail with 409 SALARY_REVISION_INVALID; employees
 *       without a salary row show up as compute skips
 *       ({@code NO_BASIC_SALARY}) — the same surfacing mechanism HR
 *       already uses for other gaps.</li>
 *   <li>{@code employee.terminated} → close the salary row, salary
 *       component lines, and payroll settings row at the last working
 *       day, removing the employee from the monthly compute iteration.</li>
 *   <li>{@code employee.reinstated} → HR rejected the final settlement
 *       that drove the termination above; reopen the same three tables
 *       ({@code employee_salaries}, {@code employee_salary_components},
 *       {@code employee_payroll_settings}) so the employee reappears in
 *       payroll.</li>
 *   <li>{@code employee.suspended} — no-op today; surface signals when
 *       payroll consumes them later.</li>
 * </ul>
 */
@Slf4j
@Component
public class EmployeeEventListener extends EmployeeEventsConsumerSupport {

    private final EmployeePayrollSettingsService settings;
    private final EmployeeSalaryService salaries;
    private final EmployeeSalaryComponentService salaryComponentsService;

    public EmployeeEventListener(EmployeePayrollSettingsService settings,
                                 EmployeeSalaryService salaries,
                                 EmployeeSalaryComponentService salaryComponentsService,
                                 @Qualifier("outboxObjectMapper") ObjectMapper json) {
        super(json);
        this.settings = settings;
        this.salaries = salaries;
        this.salaryComponentsService = salaryComponentsService;
    }

    @KafkaListener(topics = EmployeeEvents.TOPIC, groupId = "payroll.employee-events")
    @Transactional
    public void onEmployeeEvent(String message) throws java.io.IOException {
        dispatch(message);
    }

    @Override
    protected void handle(String eventType, JsonNode payload) {
        switch (eventType) {
            case EmployeeEvents.EMPLOYEE_CREATED -> handleCreated(payload);
            case EmployeeEvents.EMPLOYEE_TERMINATED -> handleTerminated(payload);
            case EmployeeEvents.EMPLOYEE_REINSTATED -> handleReinstated(payload);
            default -> log.debug("Ignoring employee event type {}", eventType);
        }
    }

    private void handleCreated(JsonNode p) {
        long employeeId = p.path("employeeId").asLong();
        if (employeeId == 0) return;
        LocalDate hireDate = readDate(p, "joiningDate");
        if (hireDate == null) {
            log.warn("Skipping employee.created without joiningDate for {}", employeeId);
            return;
        }
        settings.ensureExists(employeeId, hireDate);
    }

    private void handleTerminated(JsonNode p) {

        long employeeId = p.path("employeeId").asLong();

        if (employeeId == 0) return;

        LocalDate lastWorkingDay = readDate(p, "lastWorkingDay");

        if (lastWorkingDay == null) lastWorkingDay = LocalDate.now();

        salaries.closeOnExit(employeeId, lastWorkingDay);

        salaryComponentsService.closeOnExit(employeeId, lastWorkingDay);

        settings.markInactive(employeeId);

    }

    /**
     * HR rejected a final settlement — {@code TerminationSaga.undoTermination}
     * restored the employee on the employee-service side; reopen the
     * three payroll tables {@code handleTerminated} closed so the
     * employee reappears in the monthly compute loop. Idempotent —
     * each reopen step below is a no-op if already reopened or if
     * nothing was closed on {@code lastWorkingDay}.
     */
    private void handleReinstated(JsonNode p) {

        long employeeId = p.path("employeeId").asLong();

        if (employeeId == 0) return;

        LocalDate lastWorkingDay = readDate(p, "lastWorkingDay");

        if (lastWorkingDay == null) {
            log.warn("Skipping employee.reinstated without lastWorkingDay for {}", employeeId);
            return;
        }

        salaries.reopenOnExit(employeeId, lastWorkingDay);

        salaryComponentsService.reopenOnExit(employeeId, lastWorkingDay);

        settings.markActive(employeeId);
    }
}
