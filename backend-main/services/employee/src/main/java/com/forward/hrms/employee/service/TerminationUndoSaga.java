package com.forward.hrms.employee.service;

import java.util.List;
import java.util.Map;

import org.springframework.http.HttpStatusCode;
import org.springframework.stereotype.Component;

import com.fasterxml.jackson.databind.node.ObjectNode;
import com.forward.hrms.commons.core.error.UpstreamError;
import com.forward.hrms.commons.kafka.outbox.AuditEmitter;
import com.forward.hrms.commons.saga.Saga;
import com.forward.hrms.commons.saga.SagaContext;
import com.forward.hrms.commons.saga.SagaStep;
import com.forward.hrms.commons.saga.StepResult;
import com.forward.hrms.employee.client.AuthClient;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;


/**
 * Reverse of {@link TerminationDisableSaga} — re-enables ESS/auth access
 * after HR rejects a final settlement and {@code TerminationSaga.undoTermination}
 * restores the employee's employment status.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class TerminationUndoSaga implements Saga {

    public static final String NAME = "TERMINATION_UNDO_AUTH";

    private final AuthClient authClient;
    private final AuditEmitter audit;

    @Override public String name() { return NAME; }

    @Override public List<SagaStep> steps() {
        return List.of(new EnableAuthUser());
    }

    private final class EnableAuthUser implements SagaStep {

        @Override public String name() { return "ENABLE_AUTH_USER"; }

        @Override public StepResult execute(SagaContext ctx) {
            ObjectNode p = ctx.payload();
            long employeeId = p.path("employeeId").asLong();
            long userId = p.path("userId").asLong();
            if (userId <= 0) {
                return StepResult.failed("PAYLOAD_INCOMPLETE", "userId missing or invalid");
            }
            Map<String, String> identity = ctx.identity();

            try {
                authClient.enableUser(userId, identity);
            } catch (UpstreamError ex) {
                return classify(ex);
            }

            audit.emit("employee", String.valueOf(employeeId), "ess.reenabled",
                    null, Map.of("userId", userId), true);
            return StepResult.done();
        }

        private StepResult classify(UpstreamError ex) {
            HttpStatusCode status = AuthClient.statusOf(ex).orElse(null);
            String message = ex.getMessage();
            if (status != null && status.value() == 404) return StepResult.done();
            if (status != null && status.is4xxClientError()) {
                return StepResult.failed("AUTH_REJECTED_" + status.value(), message);
            }
            return StepResult.retry(message != null ? message : "auth call failed");
        }
    }
}
