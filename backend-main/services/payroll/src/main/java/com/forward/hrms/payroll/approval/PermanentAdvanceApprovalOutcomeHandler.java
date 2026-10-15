package com.forward.hrms.payroll.approval;

import com.forward.hrms.commons.approval.service.ApprovalService;
import com.forward.hrms.commons.approval.spi.ApprovalOutcomeHandler;
import com.forward.hrms.commons.core.error.NotFoundError;
import com.forward.hrms.commons.kafka.outbox.AuditEmitter;
import com.forward.hrms.payroll.entity.PermanentAdvance;
import com.forward.hrms.payroll.enums.AdvanceStatus;
import com.forward.hrms.payroll.enums.PayrollErrorCode;
import com.forward.hrms.payroll.repository.PermanentAdvanceRepository;

import lombok.RequiredArgsConstructor;

import org.springframework.stereotype.Component;

import java.util.Map;

/**
 * Handles the post-decision effects for permanent advance approvals. Runs
 * inside the ApprovalService MANDATORY transaction — changes commit together.
 */
@Component
@RequiredArgsConstructor
public class PermanentAdvanceApprovalOutcomeHandler implements ApprovalOutcomeHandler {

    private final PermanentAdvanceRepository advances;
    private final AuditEmitter audit;

    @Override
    public String requestType() {
        return "PERMANENT_ADVANCE";
    }

    @Override
    public void onApproved(long requestId, ApprovalService.Decision decision) {
        PermanentAdvance a = require(requestId);
        if (decision.isFinal()) {
            a.setStatus(AdvanceStatus.APPROVED);
            audit.emit("permanent_advance", String.valueOf(requestId), "accounts_approved",
                    null, Map.of("by", decision.actor().userId()), true);
            return;
        }
        if ("Manager Approval".equals(decision.step().getLabel())) {
            a.setStatus(AdvanceStatus.MANAGER_APPROVED);
            audit.emit("permanent_advance", String.valueOf(requestId), "manager_approved",
                    null, Map.of("by", decision.actor().userId()), true);
        } else {
            a.setStatus(AdvanceStatus.HR_APPROVED);
            audit.emit("permanent_advance", String.valueOf(requestId), "hr_approved",
                    null, Map.of("by", decision.actor().userId()), true);
        }
    }

    @Override
    public void onRejected(long requestId, String reason, ApprovalService.Decision decision) {
        PermanentAdvance a = require(requestId);
        a.setStatus(AdvanceStatus.REJECTED);
        a.setRejectionReason(reason);
        audit.emit("permanent_advance", String.valueOf(requestId), "rejected",
                null, Map.of("by", decision.actor().userId(), "reason", reason), true);
    }

    private PermanentAdvance require(long id) {
        return advances.findById(id)
                .orElseThrow(() -> new NotFoundError(PayrollErrorCode.PERMANENT_ADVANCE_NOT_FOUND,
                        "permanent advance not found"));
    }
}
