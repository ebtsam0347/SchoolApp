package com.forward.hrms.employee.approval;

import com.forward.hrms.commons.approval.service.ApprovalService;
import com.forward.hrms.commons.approval.spi.ApprovalOutcomeHandler;
import com.forward.hrms.commons.core.error.NotFoundError;
import com.forward.hrms.commons.kafka.outbox.AuditEmitter;
import com.forward.hrms.employee.entity.EmployeeRequisition;
import com.forward.hrms.employee.enums.EmployeeErrorCode;
import com.forward.hrms.employee.enums.RequisitionStatus;
import com.forward.hrms.employee.repository.EmployeeRequisitionRepository;

import lombok.RequiredArgsConstructor;

import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.Map;

/**
 * Handles the post-decision effects for REPLACEMENT requisition approvals.
 * Runs inside the ApprovalService MANDATORY transaction — changes commit together.
 */
@Component
@RequiredArgsConstructor
public class RequisitionReplacementApprovalOutcomeHandler implements ApprovalOutcomeHandler {

    private final EmployeeRequisitionRepository requisitions;
    private final AuditEmitter audit;

    @Override
    public String requestType() {
        return "REQUISITION_REPLACEMENT";
    }

    @Override
    public void onApproved(long requestId, ApprovalService.Decision decision) {
        if (!decision.isFinal()) return;
        EmployeeRequisition r = require(requestId);
        r.setStatus(RequisitionStatus.APPROVED);
        r.setApprovedByUserId(decision.actor().userId());
        r.setApprovedAt(LocalDateTime.now());
        audit.emit("requisition", String.valueOf(requestId), "approved",
                null, Map.of("status", "APPROVED",
                        "approvedBy", decision.actor().username()), true);
    }

    @Override
    public void onRejected(long requestId, String reason, ApprovalService.Decision decision) {
        EmployeeRequisition r = require(requestId);
        r.setStatus(RequisitionStatus.REJECTED);
        r.setRejectReason(reason);
        audit.emit("requisition", String.valueOf(requestId), "rejected",
                null, Map.of("status", "REJECTED", "reason", reason), true);
    }

    private EmployeeRequisition require(long id) {
        return requisitions.findById(id)
                .orElseThrow(() -> new NotFoundError(
                        EmployeeErrorCode.REQUISITION_NOT_FOUND, "requisition not found"));
    }
}
