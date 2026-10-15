package com.forward.hrms.payroll.service;

import com.forward.hrms.commons.saga.Saga;
import com.forward.hrms.commons.saga.SagaContext;
import com.forward.hrms.commons.saga.SagaStep;
import com.forward.hrms.commons.saga.StepResult;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

@Slf4j
@Component
@RequiredArgsConstructor
public class BonusTransactionComputeSaga implements Saga {

    public static final String NAME = "BONUS_TRANSACTION_COMPUTE";

    private final BonusTransactionComputeService computeService;

    @Override
    public String name() {

        return NAME;
    }

    @Override
    public List<SagaStep> steps() {

        return List.of(new ComputeBonusTransactions());
    }

    private final class ComputeBonusTransactions implements SagaStep {
        @Override
        public String name() {

            return "COMPUTE_BONUS_TRANSACTIONS";
        }

        @Override
        public StepResult execute(SagaContext ctx) {
            try {
                long bonusNoSettingsId = ctx.payload().path("bonusNoSettingsId").asLong();
                Map<String, String> identity = ctx.identity();
                computeService.runComputeStep(bonusNoSettingsId, identity);
                return StepResult.done();
            } catch (RuntimeException ex) {
                log.warn("COMPUTE_BONUS_TRANSACTIONS retry: {}", ex.getMessage());
                return StepResult.retry(ex.getClass().getSimpleName() + ": "
                        + (ex.getMessage() == null ? "" : ex.getMessage()));
            }
        }
    }
}
