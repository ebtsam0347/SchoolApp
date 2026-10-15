package com.forward.hrms.employee.events.payroll;


import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.forward.hrms.employee.service.ExitService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.io.IOException;
import java.time.LocalDate;

@Slf4j
@Component
public class PayrollEventListener extends PayrollEventsConsumerSupport {

    private final ExitService exitService;

    public PayrollEventListener(
            ExitService exitService,
            @Qualifier("outboxObjectMapper") ObjectMapper json) {

        super(json);
        this.exitService = exitService;
    }

    @KafkaListener(
            topics = PayrollEvents.TOPIC,
            groupId = "employee.payroll-events"
    )

    @Transactional
    public void onPayrollEvent(String message) throws IOException {
        log.info("Received Payroll Event:\n{}", message);
        dispatch(message);
        log.info("Dispatching Payroll Event Type = {}",  message);
    }

    @Override
    protected void handle(
            String eventType,
            JsonNode payload) {

        switch (eventType) {

            case PayrollEvents.FINAL_SETTLEMENT_CREATED -> handleFinalSettlementCreated(payload);

            case PayrollEvents.FINAL_SETTLEMENT_PAID -> handleFinalSettlementPaid(payload);

            case PayrollEvents.FINAL_SETTLEMENT_REJECTED -> handleFinalSettlementRejected(payload);

            case PayrollEvents.FINAL_SETTLEMENT_CANCELLED -> handleFinalSettlementCancelled(payload);

            default -> log.debug("Ignoring payroll event {}", eventType);
        }
    }


    /**
     * HR has created a Final Settlement in Payroll.
     * <p>
     * Employee Service creates the Exit record AND terminates the
     * employee immediately (employment status flips to TERMINATED,
     * ESS/auth access is disabled). Access is revoked as soon as the
     * settlement is created — it does not wait for payment.
     */
    private void handleFinalSettlementCreated(JsonNode payload) {

        log.info("Received FINAL_SETTLEMENT_CREATED payload: {}", payload);

        long employeeId = payload.path("employeeId").asLong();

        long settlementId = payload.path("settlementId").asLong();

        log.info("employeeId={}, settlementId={}", employeeId, settlementId);

        if (employeeId <= 0) {

            log.warn(
                    "Ignoring payroll.final_settlement.created because employeeId is missing."
            );

            return;
        }

        LocalDate lastWorkingDay = readDate(payload, "lastWorkingDay");

        if (lastWorkingDay == null) {

            log.error(
                    "Payroll settlement {} for employee {} has no lastWorkingDay.",
                    settlementId,
                    employeeId
            );

            return;
        }

        String terminationReason = readString(payload, "terminationReason");

        try {

            log.info("Received FINAL_SETTLEMENT_CREATED payload reached in try block: {}", payload);

            exitService.createPendingExit(
                    settlementId,
                    employeeId,
                    lastWorkingDay,
                    terminationReason
            );

            log.info(
                    "Pending exit created for employee {} from settlement {}.",
                    employeeId,
                    settlementId
            );

        } catch (Exception ex) {

            log.error(
                    "Failed creating pending exit for employee {} settlement {}.",
                    employeeId,
                    settlementId,
                    ex
            );

            throw ex;
        }
    }


    /**
     * Final Settlement has been paid by Accounts.
     * <p>
     * Employee termination already happened when the settlement was
     * created — this handler is now idempotent-only: it exists for the
     * case where the paid event arrives (or replays) after termination
     * already completed, and simply confirms/no-ops via
     * ExitService.finalizeTermination's existing idempotency guards.
     */
    private void handleFinalSettlementPaid(JsonNode payload) {

        long employeeId = payload.path("employeeId").asLong();
        long settlementId = payload.path("settlementId").asLong();

        if (employeeId <= 0) {

            log.warn("Ignoring payroll.final_settlement.paid because employeeId is missing.");

            return;
        }

        LocalDate lastWorkingDay = readDate(payload, "lastWorkingDay");

        if (lastWorkingDay == null) {

            log.error(
                    "Payroll settlement {} for employee {} has no lastWorkingDay.",
                    settlementId,
                    employeeId
            );

            return;
        }

        try {

            exitService.finalizeTermination(
                    employeeId,
                    settlementId,
                    lastWorkingDay
            );

            log.info(
                    "Employee {} termination finalized after settlement {} payment.",
                    employeeId,
                    settlementId
            );

        } catch (Exception ex) {

            log.error(
                    "Failed to finalize employee {} after settlement {} payment.",
                    employeeId,
                    settlementId,
                    ex
            );

            throw ex;
        }
    }


    /**
     * HR rejected a DRAFT final settlement. Payroll has already reversed
     * its own financial closing — undo the termination this service
     * performed at settlement creation (status, contracts, history,
     * suspensions, ESS/auth).
     */
    private void handleFinalSettlementRejected(JsonNode payload) {

        long employeeId = payload.path("employeeId").asLong();
        long settlementId = payload.path("settlementId").asLong();

        if (employeeId <= 0) {

            log.warn("Ignoring payroll.final_settlement.rejected because employeeId is missing.");

            return;
        }

        String reason = readString(payload, "reason");

        try {

            exitService.undoTermination(employeeId, settlementId, reason, "rejected");

            log.info(
                    "Employee {} termination undone after settlement {} rejection.",
                    employeeId,
                    settlementId
            );

        } catch (Exception ex) {

            log.error(
                    "Failed to undo termination for employee {} settlement {}.",
                    employeeId,
                    settlementId,
                    ex
            );

            throw ex;
        }
    }


    /**
     * HR cancelled a DRAFT or HR_APPROVED final settlement. Payroll has
     * already reversed its own financial closing — undo the termination
     * this service performed at settlement creation (status, contracts,
     * history, suspensions, ESS/auth). Identical handling to
     * {@link #handleFinalSettlementRejected}; only the audit/metric
     * wording (via the "cancelled" trigger verb) differs.
     */
    private void handleFinalSettlementCancelled(JsonNode payload) {

        long employeeId = payload.path("employeeId").asLong();
        long settlementId = payload.path("settlementId").asLong();

        if (employeeId <= 0) {

            log.warn("Ignoring payroll.final_settlement.cancelled because employeeId is missing.");

            return;
        }

        String reason = readString(payload, "reason");

        try {

            exitService.undoTermination(employeeId, settlementId, reason, "cancelled");

            log.info(
                    "Employee {} termination undone after settlement {} cancellation.",
                    employeeId,
                    settlementId
            );

        } catch (Exception ex) {

            log.error(
                    "Failed to undo termination for employee {} settlement {}.",
                    employeeId,
                    settlementId,
                    ex
            );

            throw ex;
        }
    }
}