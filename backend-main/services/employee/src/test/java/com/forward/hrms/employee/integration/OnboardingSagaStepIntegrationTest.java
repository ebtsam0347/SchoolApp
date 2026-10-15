package com.forward.hrms.employee.integration;

import com.fasterxml.jackson.databind.node.ObjectNode;
import com.forward.hrms.commons.core.error.CommonErrorCode;
import com.forward.hrms.commons.core.error.UpstreamError;
import com.forward.hrms.commons.saga.SagaContext;
import com.forward.hrms.commons.saga.SagaStep;
import com.forward.hrms.commons.saga.StepResult;
import com.forward.hrms.commons.testsupport.containers.WithTestcontainers;
import com.forward.hrms.employee.client.AuthClient;
import com.forward.hrms.employee.entity.Employee;
import com.forward.hrms.employee.enums.EmploymentStatus;
import com.forward.hrms.employee.repository.EmployeeRepository;
import com.forward.hrms.employee.service.OnboardingSaga;

import com.fasterxml.jackson.databind.ObjectMapper;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.client.HttpClientErrorException;

import java.time.LocalDate;
import java.util.Map;
import java.util.function.Function;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Drives {@link OnboardingSaga} step-by-step. Bypasses the
 * {@code SagaRunner} polling loop — instantiates a {@link SagaContext}
 * directly and calls {@code step.execute(ctx)}. The kickoff transaction
 * (employee row + outbox + audit) is exercised in a separate test;
 * here the focus is the {@code PROVISION_ESS} step's classification:
 * 4xx → FAILED, 5xx → RETRY, missing payload fields → FAILED, vanished
 * employee → FAILED with orphan-cleanup, happy path → DONE + userId
 * patched.
 */
@SpringBootTest
@WithTestcontainers
class OnboardingSagaStepIntegrationTest {

    @Autowired OnboardingSaga saga;
    @Autowired EmployeeRepository employees;
    @Autowired PlatformTransactionManager txm;

    @Autowired
    @Qualifier("sagaObjectMapper")
    ObjectMapper sagaJson;

    @MockitoBean AuthClient authClient;

    private SagaStep provisionEss;
    private TransactionTemplate tx;

    @BeforeEach
    void resolveStep() {
        provisionEss = saga.steps().get(0);
        assertThat(provisionEss.name()).isEqualTo("PROVISION_ESS");
        // The SagaRunner wraps advanceOne in @Transactional, so the step's
        // audit emits join an active tx. Mirror that here.
        tx = new TransactionTemplate(txm);
    }

    private StepResult execute(SagaContext ctx) {
        return tx.execute(s -> provisionEss.execute(ctx));
    }

    @Test
    void provisionEssDisabled_returnsDoneWithoutCallingAuth() {
        long employeeId = persistEmployeeWithoutUserId();
        ObjectNode payload = newPayload(employeeId);
        payload.put("provisionEss", false);

        StepResult result = execute(ctx(payload, name -> "ignored"));

        assertThat(result).isInstanceOf(StepResult.Done.class);
        verify(authClient, never()).createUser(any(), any(), anyString());
    }

    @Test
    void happyPath_callsAuth_patchesUserIdOnEmployee_writesUserIdToPayload() {
        long employeeId = persistEmployeeWithoutUserId();
        // Unique per run — DB persists across test classes (Testcontainers
        // reuse), and employees.user_id is unique.
        long allocatedUserId = System.nanoTime() & 0x7fffffffL;

        when(authClient.createUser(any(), any(), anyString()))
                .thenReturn(new AuthClient.CreateUserResponse(
                        allocatedUserId, "ali.khan", "Ali Khan", "ali@example.com"));

        ObjectNode payload = newPayload(employeeId);
        payload.put("provisionEss", true);
        payload.put("username", "ali.khan");
        payload.put("email", "ali@example.com");

        StepResult result = execute(ctx(payload,
                name -> "temporaryPassword".equals(name) ? "Temp!1234" : null));

        assertThat(result).isInstanceOf(StepResult.Done.class);
        verify(authClient, times(1)).createUser(any(), any(), anyString());

        Employee reloaded = employees.findById(employeeId).orElseThrow();
        assertThat(reloaded.getUserId()).isEqualTo(allocatedUserId);
        assertThat(payload.path("userId").asLong()).isEqualTo(allocatedUserId);
    }

    @Test
    void missingTemporaryPassword_returnsFailed_doesNotCallAuth() {
        long employeeId = persistEmployeeWithoutUserId();
        ObjectNode payload = newPayload(employeeId);
        payload.put("provisionEss", true);
        payload.put("username", "ali.khan");
        payload.put("email", "ali@example.com");

        // Secret lookup returns null → SagaContext.secret throws → step catches.
        StepResult result = execute(ctx(payload, name -> null));

        assertThat(result).isInstanceOf(StepResult.Failed.class);
        StepResult.Failed f = (StepResult.Failed) result;
        assertThat(f.code()).isEqualTo("PAYLOAD_INCOMPLETE");
        verify(authClient, never()).createUser(any(), any(), anyString());
    }

    @Test
    void authReturns4xx_returnsFailed_withCodeFromStatus() {
        long employeeId = persistEmployeeWithoutUserId();
        when(authClient.createUser(any(), any(), anyString())).thenThrow(
                new UpstreamError(CommonErrorCode.DEPENDENCY_UNAVAILABLE,
                        "username taken",
                        new HttpClientErrorException(org.springframework.http.HttpStatus.CONFLICT)));

        ObjectNode payload = newPayload(employeeId);
        payload.put("provisionEss", true);
        payload.put("username", "ali.khan");
        payload.put("email", "ali@example.com");

        StepResult result = execute(ctx(payload, name -> "Temp!1234"));

        assertThat(result).isInstanceOf(StepResult.Failed.class);
        StepResult.Failed f = (StepResult.Failed) result;
        assertThat(f.code()).isEqualTo("AUTH_REJECTED_409");

        Employee reloaded = employees.findById(employeeId).orElseThrow();
        assertThat(reloaded.getUserId()).isNull();
    }

    @Test
    void authReturns5xx_returnsRetry() {
        long employeeId = persistEmployeeWithoutUserId();
        when(authClient.createUser(any(), any(), anyString())).thenThrow(
                new UpstreamError(CommonErrorCode.DEPENDENCY_UNAVAILABLE,
                        "auth down",
                        new HttpClientErrorException(org.springframework.http.HttpStatus.INTERNAL_SERVER_ERROR)));

        ObjectNode payload = newPayload(employeeId);
        payload.put("provisionEss", true);
        payload.put("username", "ali.khan");
        payload.put("email", "ali@example.com");

        StepResult result = execute(ctx(payload, name -> "Temp!1234"));

        assertThat(result).isInstanceOf(StepResult.Retry.class);
    }

    @Test
    void employeeMissing_returnsEmployeeGone_disablesUserAsOrphanCleanup() {
        // Payload references an employeeId that doesn't exist — auth user
        // gets created, then the patch step finds no employee row.
        long missingEmployeeId = 9_999_999_999L;
        long allocatedUserId = (System.nanoTime() & 0x7fffffffL) + 1;

        when(authClient.createUser(any(), any(), anyString()))
                .thenReturn(new AuthClient.CreateUserResponse(
                        allocatedUserId, "ghost", "Ghost", "ghost@example.com"));

        ObjectNode payload = newPayload(missingEmployeeId);
        payload.put("provisionEss", true);
        payload.put("username", "ghost");
        payload.put("email", "ghost@example.com");

        StepResult result = execute(ctx(payload, name -> "Temp!1234"));

        assertThat(result).isInstanceOf(StepResult.Failed.class);
        assertThat(((StepResult.Failed) result).code()).isEqualTo("EMPLOYEE_GONE");
        // Orphan cleanup — disableUser called best-effort.
        verify(authClient, times(1)).disableUser(allocatedUserId, Map.of());
    }

    // ---------------------------------------------------------------- helpers

    private long persistEmployeeWithoutUserId() {
        Employee e = new Employee();
        e.setEmployeeCode("ONB-" + System.nanoTime());
        e.setFullName("Onboarding Test");
        e.setJoiningDate(LocalDate.now().minusDays(1));
        e.setEmploymentStatus(EmploymentStatus.ACTIVE);
        return employees.save(e).getId();
    }

    private ObjectNode newPayload(long employeeId) {
        ObjectNode p = sagaJson.createObjectNode();
        p.put("employeeId", employeeId);
        p.put("fullName", "Onboarding Test");
        p.put("joiningDate", LocalDate.now().minusDays(1).toString());
        p.putObject("identity"); // empty headers — saga thread, no inbound request
        return p;
    }

    private SagaContext ctx(ObjectNode payload, Function<String, String> secretLookup) {
        return new SagaContext(1L, "employee:" + payload.path("employeeId").asLong(),
                payload, 0, secretLookup);
    }
}
