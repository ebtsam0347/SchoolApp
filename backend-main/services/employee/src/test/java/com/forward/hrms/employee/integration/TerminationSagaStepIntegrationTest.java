package com.forward.hrms.employee.integration;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.forward.hrms.commons.core.error.CommonErrorCode;
import com.forward.hrms.commons.core.error.UpstreamError;
import com.forward.hrms.commons.saga.SagaContext;
import com.forward.hrms.commons.saga.SagaStep;
import com.forward.hrms.commons.saga.StepResult;
import com.forward.hrms.commons.testsupport.containers.WithTestcontainers;
import com.forward.hrms.employee.client.AuthClient;
import com.forward.hrms.employee.service.TerminationDisableSaga;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.client.HttpClientErrorException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

/**
 * {@link TerminationDisableSaga}'s single step. Validates the doctrine
 * convergence point — auth-service is the only downstream, the call is
 * keyed on userId, and 404 from auth is success (the user is already
 * gone, so the saga is done). Non-404 4xx fails permanently; 5xx
 * retries. Missing userId fails with PAYLOAD_INCOMPLETE.
 */
@SpringBootTest
@WithTestcontainers
class TerminationSagaStepIntegrationTest {

    @Autowired TerminationDisableSaga saga;
    @Autowired PlatformTransactionManager txm;

    @Autowired
    @Qualifier("sagaObjectMapper")
    ObjectMapper sagaJson;

    @MockitoBean AuthClient authClient;

    private SagaStep disableStep;
    private TransactionTemplate tx;

    @BeforeEach
    void resolveStep() {
        disableStep = saga.steps().get(0);
        assertThat(disableStep.name()).isEqualTo("DISABLE_AUTH_USER");
        tx = new TransactionTemplate(txm);
    }

    @Test
    void happyPath_callsDisableWithEmployeeTerminatedReason() {
        doNothing().when(authClient).disableUser(anyLong(), any(), anyString());

        ObjectNode payload = payload(101L, 4242L);
        StepResult result = execute(payload);

        assertThat(result).isInstanceOf(StepResult.Done.class);
        verify(authClient, times(1)).disableUser(4242L, java.util.Map.of(), "EMPLOYEE_TERMINATED");
    }

    @Test
    void missingUserId_returnsFailed_doesNotCallAuth() {
        ObjectNode payload = payload(101L, 0L);
        StepResult result = execute(payload);

        assertThat(result).isInstanceOf(StepResult.Failed.class);
        assertThat(((StepResult.Failed) result).code()).isEqualTo("PAYLOAD_INCOMPLETE");
        verify(authClient, never()).disableUser(anyLong(), any(), anyString());
    }

    @Test
    void auth404_returnsDone_userAlreadyGone() {
        // Auth says the user doesn't exist — saga is done, not stuck.
        doThrow(new UpstreamError(CommonErrorCode.DEPENDENCY_UNAVAILABLE,
                "no such user",
                new HttpClientErrorException(org.springframework.http.HttpStatus.NOT_FOUND)))
                .when(authClient).disableUser(anyLong(), any(), anyString());

        StepResult result = execute(payload(101L, 4242L));
        assertThat(result).isInstanceOf(StepResult.Done.class);
    }

    @Test
    void authNon404_4xx_returnsFailed() {
        doThrow(new UpstreamError(CommonErrorCode.DEPENDENCY_UNAVAILABLE,
                "auth rejected",
                new HttpClientErrorException(org.springframework.http.HttpStatus.FORBIDDEN)))
                .when(authClient).disableUser(anyLong(), any(), anyString());

        StepResult result = execute(payload(101L, 4242L));
        assertThat(result).isInstanceOf(StepResult.Failed.class);
        assertThat(((StepResult.Failed) result).code()).isEqualTo("AUTH_REJECTED_403");
    }

    @Test
    void auth5xx_returnsRetry() {
        doThrow(new UpstreamError(CommonErrorCode.DEPENDENCY_UNAVAILABLE,
                "auth down",
                new HttpClientErrorException(org.springframework.http.HttpStatus.INTERNAL_SERVER_ERROR)))
                .when(authClient).disableUser(anyLong(), any(), anyString());

        StepResult result = execute(payload(101L, 4242L));
        assertThat(result).isInstanceOf(StepResult.Retry.class);
    }

    // ---------------------------------------------------------------- helpers

    private ObjectNode payload(long employeeId, long userId) {
        ObjectNode p = sagaJson.createObjectNode();
        p.put("employeeId", employeeId);
        p.put("userId", userId);
        p.putObject("identity"); // empty headers — saga thread, no inbound request
        return p;
    }

    private StepResult execute(ObjectNode payload) {
        SagaContext ctx = new SagaContext(1L, "employee:" + payload.path("employeeId").asLong(),
                payload, 0, name -> null);
        return tx.execute(s -> disableStep.execute(ctx));
    }
}
