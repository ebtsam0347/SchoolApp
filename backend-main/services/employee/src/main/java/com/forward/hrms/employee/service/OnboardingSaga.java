package com.forward.hrms.employee.service;

import java.time.LocalDate;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.springframework.http.HttpStatusCode;
import org.springframework.stereotype.Component;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.forward.hrms.commons.core.error.UpstreamError;
import com.forward.hrms.commons.kafka.outbox.AuditEmitter;
import com.forward.hrms.commons.saga.Saga;
import com.forward.hrms.commons.saga.SagaContext;
import com.forward.hrms.commons.saga.SagaStep;
import com.forward.hrms.commons.saga.StepResult;
import com.forward.hrms.employee.client.AttendanceClient;
import com.forward.hrms.employee.client.AuthClient;
import com.forward.hrms.employee.client.PayrollClient;
import com.forward.hrms.employee.entity.Employee;
import com.forward.hrms.employee.repository.EmployeeRepository;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;


@Slf4j
@Component
@RequiredArgsConstructor
public class OnboardingSaga implements Saga {

    public static final String NAME = "ONBOARDING";

    private final EmployeeRepository employees;
    private final AuthClient authClient;
    private final PayrollClient payrollClient;
    private final AttendanceClient attendanceClient;
    private final AuditEmitter audit;
    private final EmployeeLifecycleMetrics lifecycle;

    @Override public String name() { return NAME; }

    @Override public List<SagaStep> steps() {
        return List.of(
                new ProvisionEss(),
                new SetBasicSalary(),
                new AssignSalaryComponents(),
                new SetBankDetails(),
                new AssignShift()
        );
    }

    // -------------------------------------------------------------------
    // Step 1 — PROVISION_ESS
    // -------------------------------------------------------------------

    private final class ProvisionEss implements SagaStep {

        @Override public String name() { return "PROVISION_ESS"; }

        @Override public StepResult execute(SagaContext ctx) {
            ObjectNode p = ctx.payload();
            if (!p.path("provisionEss").asBoolean(false)) {
                return StepResult.done();
            }

            long employeeId = p.path("employeeId").asLong();
            String username = p.path("username").asText(null);
            String fullName = p.path("fullName").asText(null);
            String email = p.path("email").asText(null);
            String tempPwd;
            try {
                tempPwd = ctx.secret("temporaryPassword");
            } catch (IllegalStateException ex) {
                return StepResult.failed("PAYLOAD_INCOMPLETE", ex.getMessage());
            }

            if (username == null) {
                return StepResult.failed("PAYLOAD_INCOMPLETE",
                        "username missing from payload");
            }

            Map<String, String> identity = ctx.identity();
            String idemKey = "onboard:emp:" + employeeId + ":ess";

            AuthClient.CreateUserResponse user;
            try {
                user = authClient.createUser(
                        new AuthClient.CreateUserRequest(username, fullName, email, tempPwd,
                                true, Set.of("EMPLOYEE")),
                        identity, idemKey);
            } catch (UpstreamError ex) {
                return classifyAuth(ex);
            }

            Employee e = employees.findById(employeeId).orElse(null);
            if (e == null) {
                try {
                    authClient.disableUser(user.id(), identity);
                    audit.emit("employee", String.valueOf(employeeId), "ess.orphan_cleanup",
                            null, Map.of("userId", user.id()), false);
                } catch (RuntimeException cleanup) {
                    log.warn("ess orphan cleanup failed for userId={} (employeeId={}): {}",
                            user.id(), employeeId, cleanup.getMessage());
                }
                return StepResult.failed("EMPLOYEE_GONE",
                        "Employee " + employeeId + " disappeared before ESS provisioning completed");
            }
            if (e.getUserId() == null) {
                e.setUserId(user.id());
                employees.save(e);
            }

            audit.emit("employee", String.valueOf(employeeId), "ess.provisioned",
                    null, Map.of("userId", user.id(), "username", user.username()), true);
            lifecycle.essProvisioned();
            p.put("userId", user.id());
            return StepResult.done();
        }

        private StepResult classifyAuth(UpstreamError ex) {
            HttpStatusCode status = AuthClient.statusOf(ex).orElse(null);
            String message = ex.getMessage();
            if (status != null && status.is4xxClientError()) {
                return StepResult.failed("AUTH_REJECTED_" + status.value(), message);
            }
            return StepResult.retry(message != null ? message : "auth call failed");
        }
    }

    // -------------------------------------------------------------------
    // Step 2 — SET_BASIC_SALARY
    // -------------------------------------------------------------------

    private final class SetBasicSalary implements SagaStep {

        @Override public String name() { return "SET_BASIC_SALARY"; }

        @Override public StepResult execute(SagaContext ctx) {
            ObjectNode p = ctx.payload();
            JsonNode salary = p.path("salary");
            if (salary.isMissingNode() || !salary.has("basic")) {
                return StepResult.done();
            }

            long employeeId = p.path("employeeId").asLong();
            long basic = salary.path("basic").asLong();
            LocalDate effectiveFrom = LocalDate.parse(p.path("joiningDate").asText());
            Map<String, String> identity = ctx.identity();
            String idemKey = "onboard:emp:" + employeeId + ":salary:basic";

            try {
                payrollClient.reviseBasicSalary(employeeId,
                        new PayrollClient.ReviseBasicSalaryRequest(basic, effectiveFrom,
                                "Initial onboarding"),
                        identity, idemKey);
            } catch (UpstreamError ex) {
                return classifyPayroll(ex);
            }

            audit.emit("employee", String.valueOf(employeeId), "salary.set",
                    null, Map.of("basic", basic, "effectiveFrom", effectiveFrom.toString()), true);
            return StepResult.done();
        }
    }

    // -------------------------------------------------------------------
    // Step 3 — ASSIGN_SALARY_COMPONENTS
    // -------------------------------------------------------------------

    private final class AssignSalaryComponents implements SagaStep {

        @Override public String name() { return "ASSIGN_SALARY_COMPONENTS"; }

        @Override public StepResult execute(SagaContext ctx) {
            ObjectNode p = ctx.payload();
            JsonNode components = p.path("salary").path("components");
            if (!components.isArray() || components.isEmpty()) {
                return StepResult.done();
            }

            long employeeId = p.path("employeeId").asLong();
            LocalDate effectiveFrom = LocalDate.parse(p.path("joiningDate").asText());
            Map<String, String> identity = ctx.identity();

            Set<Long> alreadyApplied = readAppliedSet(p);
            ObjectNode salaryNode = (ObjectNode) p.path("salary");
            ArrayNode appliedArr = salaryNode.has("componentsApplied")
                    ? (ArrayNode) salaryNode.get("componentsApplied")
                    : salaryNode.putArray("componentsApplied");

            int newlyApplied = 0;
            for (JsonNode line : components) {
                long componentId = line.path("componentId").asLong();
                if (alreadyApplied.contains(componentId)) continue;

                Long amount = line.has("amount") && !line.get("amount").isNull()
                        ? line.get("amount").asLong() : null;
                String notes = line.has("notes") && !line.get("notes").isNull()
                        ? line.get("notes").asText() : null;
                String idemKey = "onboard:emp:" + employeeId + ":comp:" + componentId;

                try {
                    payrollClient.addSalaryComponent(employeeId,
                            new PayrollClient.AddSalaryLineRequest(componentId, amount, effectiveFrom, notes),
                            identity, idemKey);
                } catch (UpstreamError ex) {
                    return classifyPayroll(ex);
                }
                appliedArr.add(componentId);
                alreadyApplied.add(componentId);
                newlyApplied++;
            }

            audit.emit("employee", String.valueOf(employeeId), "salary.components.applied",
                    null, Map.of("newlyApplied", newlyApplied, "total", alreadyApplied.size()), true);
            return StepResult.done();
        }

        private Set<Long> readAppliedSet(ObjectNode p) {
            Set<Long> out = new HashSet<>();
            JsonNode arr = p.path("salary").path("componentsApplied");
            if (arr.isArray()) arr.forEach(v -> out.add(v.asLong()));
            return out;
        }
    }

    // -------------------------------------------------------------------
    // Step 4 — SET_BANK_DETAILS
    // -------------------------------------------------------------------

    private final class SetBankDetails implements SagaStep {

        @Override public String name() { return "SET_BANK_DETAILS"; }

        @Override public StepResult execute(SagaContext ctx) {
            ObjectNode p = ctx.payload();
            JsonNode bank = p.path("salary").path("bank");
            if (bank.isMissingNode() || !bank.has("bankId")) {
                return StepResult.done();
            }

            long employeeId = p.path("employeeId").asLong();
            Map<String, String> identity = ctx.identity();
            String idemKey = "onboard:emp:" + employeeId + ":bank";

            try {
                payrollClient.putBankDetails(employeeId,
                        new PayrollClient.UpsertBankDetailsRequest(
                                bank.path("bankId").asLong(),
                                bank.path("branch").asText(null),
                                bank.path("accountTitle").asText(null),
                                bank.path("iban").asText(null),
                                bank.path("accountNumber").asText(null)),
                        identity, idemKey);
            } catch (UpstreamError ex) {
                return classifyPayroll(ex);
            }

            audit.emit("employee", String.valueOf(employeeId), "salary.bank.set",
                    null, Map.of("bankId", bank.path("bankId").asLong(),
                            "iban", bank.path("iban").asText("")), true);
            return StepResult.done();
        }
    }

    // -------------------------------------------------------------------
    // Step 5 — ASSIGN_SHIFT
    // -------------------------------------------------------------------

    private final class AssignShift implements SagaStep {

        @Override public String name() { return "ASSIGN_SHIFT"; }

        @Override public StepResult execute(SagaContext ctx) {
            ObjectNode p = ctx.payload();
            JsonNode shift = p.path("shift");
            if (shift.isMissingNode() || !shift.has("shiftId")) {
                return StepResult.done();
            }

            long employeeId = p.path("employeeId").asLong();
            long shiftId = shift.path("shiftId").asLong();
            LocalDate effectiveFrom = LocalDate.parse(p.path("joiningDate").asText());
            Map<String, String> identity = ctx.identity();
            String idemKey = "onboard:emp:" + employeeId + ":shift";

            try {
                attendanceClient.assignShift(employeeId,
                        new AttendanceClient.AssignShiftRequest(shiftId, effectiveFrom, null),
                        identity, idemKey);
            } catch (UpstreamError ex) {
                return classifyAttendance(ex);
            }

            audit.emit("employee", String.valueOf(employeeId), "shift.assigned",
                    null, Map.of("shiftId", shiftId, "effectiveFrom", effectiveFrom.toString()), true);
            return StepResult.done();
        }
    }

    // -------------------------------------------------------------------
    // shared
    // -------------------------------------------------------------------

    private static StepResult classifyPayroll(UpstreamError ex) {
        HttpStatusCode status = PayrollClient.statusOf(ex).orElse(null);
        String message = ex.getMessage();
        if (status != null && status.is4xxClientError()) {
            return StepResult.failed("PAYROLL_REJECTED_" + status.value(), message);
        }
        return StepResult.retry(message != null ? message : "payroll call failed");
    }

    private static StepResult classifyAttendance(UpstreamError ex) {
        HttpStatusCode status = AttendanceClient.statusOf(ex).orElse(null);
        String message = ex.getMessage();
        if (status != null && status.is4xxClientError()) {
            return StepResult.failed("ATTENDANCE_REJECTED_" + status.value(), message);
        }
        return StepResult.retry(message != null ? message : "attendance call failed");
    }

}
