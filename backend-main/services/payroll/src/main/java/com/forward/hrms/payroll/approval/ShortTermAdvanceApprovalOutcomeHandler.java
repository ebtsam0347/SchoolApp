package com.forward.hrms.payroll.approval;

import com.forward.hrms.commons.approval.service.ApprovalService;
import com.forward.hrms.commons.approval.spi.ApprovalOutcomeHandler;
import com.forward.hrms.commons.core.error.NotFoundError;
import com.forward.hrms.commons.kafka.outbox.AuditEmitter;
import com.forward.hrms.payroll.entity.ShortTermAdvance;
import com.forward.hrms.payroll.entity.ShortTermAdvanceBatch;
import com.forward.hrms.payroll.enums.AdvanceStatus;
import com.forward.hrms.payroll.enums.PayrollErrorCode;
import com.forward.hrms.payroll.enums.ShortTermAdvanceStatus;
import com.forward.hrms.payroll.repository.ShortTermAdvanceBatchRepository;
import com.forward.hrms.payroll.repository.ShortTermAdvanceRepository;

import lombok.RequiredArgsConstructor;

import org.springframework.stereotype.Component;

import java.util.Map;

/**
 * Handles the post-decision effects for short-term advance batch approvals,
 * including the per-employee line status flips that used to live inline in
 * {@code ShortTermAdvanceService.accountsApprove/reject}. Runs inside the
 * ApprovalService MANDATORY transaction — changes commit together.
 */
@Component
@RequiredArgsConstructor
public class ShortTermAdvanceApprovalOutcomeHandler implements ApprovalOutcomeHandler {

    private final ShortTermAdvanceBatchRepository batches;
    private final ShortTermAdvanceRepository lines;
    private final AuditEmitter audit;

    @Override
    public String requestType() {
        return "SHORT_TERM_ADVANCE";
    }

    @Override
    public void onApproved(long requestId, ApprovalService.Decision decision) {
        ShortTermAdvanceBatch b = require(requestId);
        if (!decision.isFinal()) {
            if ("Manager Approval".equals(decision.step().getLabel())) {
                b.setStatus(AdvanceStatus.MANAGER_APPROVED);
                audit.emit("short_term_advance_batch", String.valueOf(requestId), "manager_approved",
                        null, Map.of("by", decision.actor().userId()), true);
            } else {
                b.setStatus(AdvanceStatus.HR_APPROVED);
                audit.emit("short_term_advance_batch", String.valueOf(requestId), "hr_approved",
                        null, Map.of("by", decision.actor().userId()), true);
            }
            return;
        }
        b.setStatus(AdvanceStatus.APPROVED);
        for (ShortTermAdvance row : lines.findAllByBatchIdOrderByEmployeeIdAsc(requestId)) {
            if (row.getStatus() == ShortTermAdvanceStatus.PENDING) {
                row.setStatus(ShortTermAdvanceStatus.APPROVED);
            }
        }
        audit.emit("short_term_advance_batch", String.valueOf(requestId), "accounts_approved",
                null, Map.of("by", decision.actor().userId()), true);
    }

    @Override
    public void onRejected(long requestId, String reason, ApprovalService.Decision decision) {
        ShortTermAdvanceBatch b = require(requestId);
        b.setStatus(AdvanceStatus.REJECTED);
        b.setRejectionReason(reason);
        for (ShortTermAdvance row : lines.findAllByBatchIdOrderByEmployeeIdAsc(requestId)) {
            if (row.getStatus() == ShortTermAdvanceStatus.PENDING
                    || row.getStatus() == ShortTermAdvanceStatus.APPROVED) {
                row.setStatus(ShortTermAdvanceStatus.REJECTED);
                row.setRejectionReason(reason);
            }
        }
        audit.emit("short_term_advance_batch", String.valueOf(requestId), "rejected",
                null, Map.of("by", decision.actor().userId(), "reason", reason), true);
    }

    private ShortTermAdvanceBatch require(long id) {
        return batches.findById(id)
                .orElseThrow(() -> new NotFoundError(PayrollErrorCode.SHORT_TERM_BATCH_NOT_FOUND,
                        "short-term advance batch not found"));
    }
}
