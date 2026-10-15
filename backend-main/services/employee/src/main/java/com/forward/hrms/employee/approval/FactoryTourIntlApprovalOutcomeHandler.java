package com.forward.hrms.employee.approval;

import com.forward.hrms.commons.approval.service.ApprovalService;
import com.forward.hrms.commons.approval.spi.ApprovalOutcomeHandler;
import com.forward.hrms.commons.core.error.NotFoundError;
import com.forward.hrms.commons.kafka.outbox.AuditEmitter;
import com.forward.hrms.employee.entity.FactoryTour;
import com.forward.hrms.employee.enums.EmployeeErrorCode;
import com.forward.hrms.employee.enums.TourStatus;
import com.forward.hrms.employee.repository.FactoryTourRepository;

import lombok.RequiredArgsConstructor;

import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.Map;

@Component
@RequiredArgsConstructor
public class FactoryTourIntlApprovalOutcomeHandler implements ApprovalOutcomeHandler {

    private final FactoryTourRepository tours;
    private final AuditEmitter audit;

    @Override
    public String requestType() {
        return "FACTORY_TOUR_INTL";
    }

    @Override
    public void onApproved(long requestId, ApprovalService.Decision decision) {
        if (!decision.isFinal()) return;
        FactoryTour t = require(requestId);
        t.setStatus(TourStatus.APPROVED);
        t.setApprovedByUserId(decision.actor().userId());
        t.setApprovedAt(LocalDateTime.now());
        audit.emit("factory_tour", String.valueOf(requestId), "approved",
                null, Map.of("status", "APPROVED",
                        "approvedBy", decision.actor().username()), true);
    }

    @Override
    public void onRejected(long requestId, String reason, ApprovalService.Decision decision) {
        FactoryTour t = require(requestId);
        t.setStatus(TourStatus.REJECTED);
        t.setRejectReason(reason);
        audit.emit("factory_tour", String.valueOf(requestId), "rejected",
                null, Map.of("status", "REJECTED", "reason", reason), true);
    }

    private FactoryTour require(long id) {
        return tours.findById(id)
                .orElseThrow(() -> new NotFoundError(
                        EmployeeErrorCode.TOUR_NOT_FOUND, "factory tour not found"));
    }
}
