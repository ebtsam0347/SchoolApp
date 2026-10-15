package com.forward.hrms.employee.approval;

import com.forward.hrms.commons.approval.service.ApprovalService;
import com.forward.hrms.commons.approval.spi.ApprovalOutcomeHandler;
import com.forward.hrms.commons.core.error.NotFoundError;
import com.forward.hrms.commons.kafka.outbox.AuditEmitter;
import com.forward.hrms.employee.entity.travel.TravelRequestAdvance;
import com.forward.hrms.employee.enums.travel.TravelAdvanceStatus;
import com.forward.hrms.employee.enums.travel.TravelErrorCode;
import com.forward.hrms.employee.repository.travel.TravelRequestAdvanceRepository;

import lombok.RequiredArgsConstructor;

import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.Map;

/**
 * Post-decision effects for the {@code TRAVEL_ADVANCE} chain. On final
 * approval the advance is approved in full (approved amount / currency
 * default to the requested values); partial approval is a later
 * refinement carried on the decide call, not here.
 */
@Component
@RequiredArgsConstructor
public class TravelAdvanceApprovalOutcomeHandler implements ApprovalOutcomeHandler {

    private final TravelRequestAdvanceRepository advances;
    private final AuditEmitter audit;

    @Override
    public String requestType() {
        return TravelApprovalTypes.TRAVEL_ADVANCE;
    }

    @Override
    public void onApproved(long requestId, ApprovalService.Decision decision) {
        if (!decision.isFinal()) {
            return;
        }
        TravelRequestAdvance a = require(requestId);
        a.setStatus(TravelAdvanceStatus.APPROVED);
        a.setApprovedAt(LocalDateTime.now());
        a.setApprovedBy(decision.actor().userId());
        if (a.getApprovedAmount() == null) {
            a.setApprovedAmount(a.getRequestedAmount());
            a.setApprovedCurrencyCode(a.getRequestedCurrencyCode());
        }
        audit.emit("travel_advance", String.valueOf(requestId), "approved",
                null, Map.of("status", "APPROVED", "approvedBy", decision.actor().username()), true);
    }

    @Override
    public void onRejected(long requestId, String reason, ApprovalService.Decision decision) {
        TravelRequestAdvance a = require(requestId);
        a.setStatus(TravelAdvanceStatus.REJECTED);
        audit.emit("travel_advance", String.valueOf(requestId), "rejected",
                null, Map.of("status", "REJECTED", "reason", reason == null ? "" : reason), true);
    }

    private TravelRequestAdvance require(long id) {
        return advances.findById(id)
                .orElseThrow(() -> new NotFoundError(
                        TravelErrorCode.TRAVEL_ADVANCE_NOT_FOUND, "travel advance not found"));
    }
}
