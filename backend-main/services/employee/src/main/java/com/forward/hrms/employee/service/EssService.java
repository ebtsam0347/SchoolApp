package com.forward.hrms.employee.service;

import java.util.List;
import java.util.Map;

import com.forward.hrms.employee.dto.response.AnnualLeaveEligibleEmployee;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.forward.hrms.commons.core.context.CurrentUserHolder;
import com.forward.hrms.commons.core.error.ConflictError;
import com.forward.hrms.commons.core.error.NotFoundError;
import com.forward.hrms.commons.employeeevents.EmployeeEvents;
import com.forward.hrms.commons.kafka.outbox.AuditEmitter;
import com.forward.hrms.commons.kafka.outbox.Outbox;
import com.forward.hrms.commons.saga.SagaContext;
import com.forward.hrms.commons.saga.SagaService;
import com.forward.hrms.commons.saga.SagaState;
import com.forward.hrms.employee.client.AuthClient;
import com.forward.hrms.employee.dto.request.GrantEssRequest;
import com.forward.hrms.employee.dto.response.EmployeeDto;
import com.forward.hrms.employee.dto.response.EmployeeDtoMapper;
import com.forward.hrms.employee.dto.response.GrantEssResponse;
import com.forward.hrms.employee.entity.Employee;
import com.forward.hrms.employee.enums.EmployeeErrorCode;
import com.forward.hrms.employee.repository.EmployeeRepository;

@Service
public class EssService {

    private final EmployeeRepository employees;
    private final Outbox outbox;
    private final AuditEmitter audit;
    private final EmployeeLifecycleMetrics lifecycle;
    private final SagaService sagaService;
    private final ObjectMapper sagaJson;

    public EssService(EmployeeRepository employees,
                      Outbox outbox,
                      AuditEmitter audit,
                      EmployeeLifecycleMetrics lifecycle,
                      SagaService sagaService,
                      @Qualifier("sagaObjectMapper") ObjectMapper sagaJson) {
        this.employees = employees;
        this.outbox = outbox;
        this.audit = audit;
        this.lifecycle = lifecycle;
        this.sagaService = sagaService;
        this.sagaJson = sagaJson;
    }

    @Transactional
    public GrantEssResponse grant(long employeeId, GrantEssRequest req) {
        Employee e = load(employeeId);
        if (e.getUserId() != null) {
            throw new ConflictError(EmployeeErrorCode.EMPLOYEE_ALREADY_HAS_ESS,
                    "employee already has ESS");
        }
        String username = req.username() == null || req.username().isBlank()
                ? slug(e.getFullName(), e.getId())
                : req.username();
        String email = req.email() == null || req.email().isBlank() ? e.getWorkEmail() : req.email();

        ObjectNode payload = sagaJson.createObjectNode();
        payload.put("employeeId", e.getId());
        payload.put("username", username);
        payload.put("fullName", e.getFullName());
        if (email != null && !email.isBlank()) payload.put("email", email);

        SagaContext.writeIdentity(payload, AuthClient.currentRequestIdentity());

        // temporaryPassword goes to the SagaSecretStore (Redis), never the
        // saga_state.payload jsonb. Same rationale as OnboardingService.
        Map<String, String> secrets = Map.of("temporaryPassword", req.temporaryPassword());

        SagaState saga = sagaService.startWithSecrets(EssGrantSaga.NAME,
                "employee:" + e.getId() + ":ess-grant",
                payload, secrets, null);

        audit.emit("employee", String.valueOf(e.getId()), "ess_grant_requested",
                null, Map.of("username", username, "sagaId", saga.getId()), true);
        return GrantEssResponse.of(EmployeeDtoMapper.of(e), saga.getId());
    }

    @Transactional
    public EmployeeDto revoke(long employeeId) {
        Employee e = load(employeeId);
        if (e.getUserId() == null) {
            throw new ConflictError(EmployeeErrorCode.EMPLOYEE_NO_ESS,
                    "employee has no ESS to revoke");
        }
        Long userId = e.getUserId();
        String actor = CurrentUserHolder.usernameOrSystem();

        outbox.publish(EmployeeEvents.TOPIC, "employee:" + e.getId(),
                EmployeeEvents.EMPLOYEE_ESS_REVOKED,
                new EmployeeEvents.EmployeeEssRevoked(e.getId(), userId, actor));
        audit.emit("employee", String.valueOf(e.getId()), "ess_revoked",
                Map.of("userId", userId), Map.of("by", actor), true);
        lifecycle.essRevoked();
        return EmployeeDtoMapper.of(e);
    }

    private Employee load(long id) {
        return employees.findById(id)
                .orElseThrow(() -> new NotFoundError(
                        EmployeeErrorCode.EMPLOYEE_NOT_FOUND, "employee not found"));
    }

    private static String slug(String fullName, long id) {
        String base = fullName == null ? "user" : fullName.toLowerCase()
                .replaceAll("[^a-z0-9]+", ".")
                .replaceAll("(^\\.|\\.$)", "");
        if (base.isBlank()) base = "user";
        return base + "." + id;
    }

    public List<AnnualLeaveEligibleEmployee> annualLeaveEligibleEmployeeList(int year, int month){
        return employees.getAnnualLeaveEligibleEmployeeList(year, month);

    }
}
