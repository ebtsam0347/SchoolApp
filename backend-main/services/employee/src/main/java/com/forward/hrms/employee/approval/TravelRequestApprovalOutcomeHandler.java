package com.forward.hrms.employee.approval;

import com.forward.hrms.commons.approval.entity.ApprovalStep;
import com.forward.hrms.commons.approval.enums.ResolverKind;
import com.forward.hrms.commons.approval.service.ApprovalService;
import com.forward.hrms.commons.approval.spi.ApprovalOutcomeHandler;
import com.forward.hrms.commons.core.error.NotFoundError;
import com.forward.hrms.commons.kafka.outbox.AuditEmitter;
import com.forward.hrms.employee.entity.travel.TravelRequest;
import com.forward.hrms.employee.enums.travel.TravelErrorCode;
import com.forward.hrms.employee.enums.travel.TravelRequestStatus;
import com.forward.hrms.employee.repository.travel.TravelRequestRepository;

import lombok.RequiredArgsConstructor;

import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Post-decision effects for the {@code TRAVEL_REQUEST} chain
 * (TRANSPORT &rarr; DD/Requester's Head, final). Runs inside the
 * {@link ApprovalService} MANDATORY transaction, so its writes commit or
 * roll back with the step write.
 *
 * <p>The travel-request {@code status} is driven entirely from here: the
 * Transport approval advances it to {@code DD_PENDING}, and the DD/Head
 * approval is final and yields {@link TravelRequestStatus#APPROVED} directly
 * — there is no HR stage in this chain. Each stage's rejection is terminal —
 * {@code TRANSPORT_REJECTED} / {@code DD_REJECTED} — with the generic engine
 * already having skipped the remaining stages.
 */
@Component
@RequiredArgsConstructor
public class TravelRequestApprovalOutcomeHandler implements ApprovalOutcomeHandler {

    private final TravelRequestRepository requests;
    private final AuditEmitter audit;

    /** Which stage of the TRANSPORT &rarr; DD/Requester's Head chain a decided step belongs to. */
    private enum Stage { TRANSPORT, DD, OTHER }

    @Override
    public String requestType() {
        return TravelApprovalTypes.TRAVEL_REQUEST;
    }

    @Override
    public void onApproved(long requestId, ApprovalService.Decision decision) {
        TravelRequest r = require(requestId);
        Stage stage = stageOf(decision.step());

        switch (stage) {
            case TRANSPORT -> {
                if (decision.isFinal()) {
                    finalApprove(requestId, r, decision);
                } else {
                    r.setStatus(TravelRequestStatus.DD_PENDING);
                    emit(requestId, "transport_approved", TravelRequestStatus.DD_PENDING, decision);
                }
            }
            case DD, OTHER -> {
                if (decision.isFinal()) {
                    finalApprove(requestId, r, decision);
                }
            }
        }
    }

    @Override
    public void onRejected(long requestId, String reason, ApprovalService.Decision decision) {
        TravelRequest r = require(requestId);
        Stage stage = stageOf(decision.step());

        TravelRequestStatus status = switch (stage) {
            case TRANSPORT -> TravelRequestStatus.TRANSPORT_REJECTED;
            case DD -> TravelRequestStatus.DD_REJECTED;
            case OTHER -> TravelRequestStatus.REJECTED;
        };
        String action = switch (stage) {
            case TRANSPORT -> "transport_rejected";
            case DD -> "dd_rejected";
            case OTHER -> "rejected";
        };

        r.setStatus(status);
        Map<String, Object> after = new LinkedHashMap<>();
        after.put("status", status.name());
        after.put("reason", reason == null ? "" : reason);
        after.put("rejectedBy", decision.actor().username());
        audit.emit("travel_request", String.valueOf(requestId), action, null, after, true);
    }

    // ── helpers ──────────────────────────────────────────────────────────────

    private static Stage stageOf(ApprovalStep step) {
        if (TravelApprovalTypes.TRANSPORT_VERIFY_PERMISSION.equals(step.getApprovePermission())) {
            return Stage.TRANSPORT;
        }
        if (step.getResolverKind() == ResolverKind.MANAGER) {
            return Stage.DD;
        }
        return Stage.OTHER;
    }

    private void finalApprove(long requestId, TravelRequest r, ApprovalService.Decision decision) {
        r.setStatus(TravelRequestStatus.APPROVED);
        r.setApprovedAt(LocalDateTime.now());
        emit(requestId, "approved", TravelRequestStatus.APPROVED, decision);
    }

    private void emit(long requestId, String action, TravelRequestStatus status,
                      ApprovalService.Decision decision) {
        Map<String, Object> after = new LinkedHashMap<>();
        after.put("status", status.name());
        after.put("approvedBy", decision.actor().username());
        audit.emit("travel_request", String.valueOf(requestId), action, null, after, true);
    }

    private TravelRequest require(long id) {
        return requests.findById(id)
                .orElseThrow(() -> new NotFoundError(
                        TravelErrorCode.TRAVEL_REQUEST_NOT_FOUND, "travel request not found"));
    }
}
