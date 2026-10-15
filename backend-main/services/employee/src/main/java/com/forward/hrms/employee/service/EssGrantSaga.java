package com.forward.hrms.employee.service;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.springframework.http.HttpStatusCode;
import org.springframework.stereotype.Component;

import com.fasterxml.jackson.databind.node.ObjectNode;
import com.forward.hrms.commons.core.error.UpstreamError;
import com.forward.hrms.commons.employeeevents.EmployeeEvents;
import com.forward.hrms.commons.kafka.outbox.AuditEmitter;
import com.forward.hrms.commons.kafka.outbox.Outbox;
import com.forward.hrms.commons.saga.Saga;
import com.forward.hrms.commons.saga.SagaContext;
import com.forward.hrms.commons.saga.SagaStep;
import com.forward.hrms.commons.saga.StepResult;
import com.forward.hrms.employee.client.AuthClient;
import com.forward.hrms.employee.entity.Employee;
import com.forward.hrms.employee.repository.EmployeeRepository;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;


@Slf4j
@Component
@RequiredArgsConstructor
public class EssGrantSaga implements Saga {

    public static final String NAME = "ESS_GRANT";

    private final EmployeeRepository employees;
    private final AuthClient authClient;
    private final Outbox outbox;
    private final AuditEmitter audit;
    private final EmployeeLifecycleMetrics lifecycle;

    @Override public String name() { return NAME; }

    @Override public List<SagaStep> steps() {
        return List.of(new ProvisionEss());
    }

    private final class ProvisionEss implements SagaStep {

        @Override public String name() { return "PROVISION_ESS"; }

        @Override public StepResult execute(SagaContext ctx) {
            ObjectNode p = ctx.payload();
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
                return StepResult.failed("PAYLOAD_INCOMPLETE", "username missing from payload");
            }

            Map<String, String> identity = ctx.identity();
            String idemKey = "ess-grant:emp:" + employeeId;

            AuthClient.CreateUserResponse user;
            try {
                user = authClient.createUser(
                        new AuthClient.CreateUserRequest(username, fullName, email, tempPwd,
                                true, Set.of("EMPLOYEE")),
                        identity, idemKey);
            } catch (UpstreamError ex) {
                return classify(ex);
            }

            Employee e = employees.findById(employeeId).orElse(null);
            if (e == null) {
                // Same orphan-cleanup shape as OnboardingSaga.ProvisionEss.
                try {
                    authClient.disableUser(user.id(), identity);
                    audit.emit("employee", String.valueOf(employeeId), "ess.orphan_cleanup",
                            null, Map.of("userId", user.id()), false);
                } catch (RuntimeException cleanup) {
                    log.warn("ess-grant orphan cleanup failed for userId={} (employeeId={}): {}",
                            user.id(), employeeId, cleanup.getMessage());
                }
                return StepResult.failed("EMPLOYEE_GONE",
                        "Employee " + employeeId + " disappeared before ESS grant completed");
            }
            if (e.getUserId() == null) {
                e.setUserId(user.id());
                employees.save(e);
            }

            outbox.publish(EmployeeEvents.TOPIC, "employee:" + e.getId(),
                    EmployeeEvents.EMPLOYEE_ESS_GRANTED,
                    new EmployeeEvents.EmployeeEssGranted(e.getId(), user.id(), LocalDateTime.now()));
            audit.emit("employee", String.valueOf(e.getId()), "ess_granted",
                    null, Map.of("userId", user.id(), "username", user.username()), true);
            lifecycle.essProvisioned();

            p.put("userId", user.id());
            return StepResult.done();
        }

        private StepResult classify(UpstreamError ex) {
            HttpStatusCode status = AuthClient.statusOf(ex).orElse(null);
            String message = ex.getMessage();
            if (status != null && status.is4xxClientError()) {
                return StepResult.failed("AUTH_REJECTED_" + status.value(), message);
            }
            return StepResult.retry(message != null ? message : "auth call failed");
        }
    }
}
