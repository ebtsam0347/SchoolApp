package com.forward.hrms.employee.approval;

import com.forward.hrms.commons.approval.service.ApprovalService;
import com.forward.hrms.commons.approval.spi.ApprovalOutcomeHandler;
import com.forward.hrms.commons.core.error.NotFoundError;
import com.forward.hrms.commons.kafka.outbox.AuditEmitter;
import com.forward.hrms.employee.entity.travel.TravelRequestSettlement;
import com.forward.hrms.employee.enums.travel.TravelErrorCode;
import com.forward.hrms.employee.enums.travel.TravelSettlementStatus;
import com.forward.hrms.employee.repository.travel.TravelRequestSettlementRepository;

import lombok.RequiredArgsConstructor;

import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.Map;

/**
 * Post-decision effects for the {@code TRAVEL_SETTLEMENT} chain — now a
 * 2-stage hierarchy: HR Settlement Verification (stage 1), then Accounts
 * Payment Confirmation (stage 2).
 * <p>
 * Stage 1 (HR) approval is never final (stage 2 always follows), so it
 * moves the settlement to {@code HR_APPROVED} here. Stage 2 (Accounts)
 * approval is always final, but is driven exclusively through
 * {@link com.forward.hrms.employee.service.travel.TravelSettlementService#settle}
 * — that method calls {@code approvals.approve(...)} itself to close the
 * chain and then applies the {@code PAID} domain effects (payment
 * reference, linked-advance settlement, trip completion) in the same
 * transaction, so there is nothing left for this handler to do on the
 * final decision.
 */
@Component
@RequiredArgsConstructor
public class TravelSettlementApprovalOutcomeHandler implements ApprovalOutcomeHandler {

    private final TravelRequestSettlementRepository settlements;
    private final AuditEmitter audit;

    @Override
    public String requestType() {
        return TravelApprovalTypes.TRAVEL_SETTLEMENT;
    }

    @Override
    public void onApproved(long requestId, ApprovalService.Decision decision) {
        if (decision.isFinal()) {
            return;
        }
        TravelRequestSettlement s = require(requestId);
        s.setStatus(TravelSettlementStatus.HR_APPROVED);
        s.setApprovedAt(LocalDateTime.now());
        s.setApprovedBy(decision.actor().userId());
        audit.emit("travel_settlement", String.valueOf(requestId), "hr_approved",
                null, Map.of("status", "HR_APPROVED", "approvedBy", decision.actor().username()), true);
    }

    @Override
    public void onRejected(long requestId, String reason, ApprovalService.Decision decision) {
        TravelRequestSettlement s = require(requestId);
        s.setStatus(TravelSettlementStatus.REJECTED);
        audit.emit("travel_settlement", String.valueOf(requestId), "rejected",
                null, Map.of("status", "REJECTED", "reason", reason == null ? "" : reason), true);
    }

    private TravelRequestSettlement require(long id) {
        return settlements.findById(id)
                .orElseThrow(() -> new NotFoundError(
                        TravelErrorCode.TRAVEL_SETTLEMENT_NOT_FOUND, "travel settlement not found"));
    }
}
