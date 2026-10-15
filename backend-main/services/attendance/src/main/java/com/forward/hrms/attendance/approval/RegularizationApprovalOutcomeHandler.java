package com.forward.hrms.attendance.approval;

import com.forward.hrms.attendance.entity.RegularizationRequest;
import com.forward.hrms.attendance.enums.AttendanceErrorCode;
import com.forward.hrms.attendance.enums.RegularizationStatus;
import com.forward.hrms.attendance.enums.RequestType;
import com.forward.hrms.attendance.events.AttendanceEvents;
import com.forward.hrms.attendance.repository.RegularizationRequestRepository;
import com.forward.hrms.attendance.service.PunchService;
import com.forward.hrms.commons.approval.entity.ApprovalStep;
import com.forward.hrms.commons.approval.service.ApprovalService;
import com.forward.hrms.commons.approval.spi.ApprovalOutcomeHandler;
import com.forward.hrms.commons.core.error.NotFoundError;
import com.forward.hrms.commons.kafka.outbox.AuditEmitter;
import com.forward.hrms.commons.kafka.outbox.Outbox;

import lombok.RequiredArgsConstructor;

import org.springframework.stereotype.Component;

import java.util.Map;

/**
 * Post-decision effects for REGULARIZATION approvals. Runs inside the
 * ApprovalService's MANDATORY transaction — changes commit together.
 */
@Component
@RequiredArgsConstructor
public class RegularizationApprovalOutcomeHandler implements ApprovalOutcomeHandler {

    private final RegularizationRequestRepository regularizations;
    private final PunchService punches;
    private final Outbox outbox;
    private final AuditEmitter audit;

    @Override
    public String requestType() {
        return RequestType.REGULARIZATION.name();
    }

    @Override
    public void onApproved(long requestId, ApprovalService.Decision decision) {
        RegularizationRequest r = require(requestId);
        if (!decision.isFinal()) {
            outbox.publish(AttendanceEvents.TOPIC, "regularization:" + requestId,
                    AttendanceEvents.REGULARIZATION_STAGE_APPROVED,
                    new AttendanceEvents.RegularizationStageApproved(
                            requestId, r.getEmployeeId(),
                            decision.step().getStageNo(), decision.step().getLabel(),
                            decision.actor().userId(), nextStage(decision.nextPending())));
            return;
        }
        r.setStatus(RegularizationStatus.APPROVED);
        // §7.attendance #16 — write both punches first, then run a single
        // recompute for the day. Each writeRegularizedPunch fired its own
        // recompute → two recomputes + two punch_recorded events for the
        // same projection day.
        boolean wroteAny = false;
        if (r.getRequestedCheckIn() != null) {
            wroteAny |= punches.writeRegularizedPunchNoRecompute(r.getEmployeeId(), r.getRequestedCheckIn(),
                    r.getAttendanceDate(), r.getId()).isPresent();
        }
        if (r.getRequestedCheckOut() != null) {
            wroteAny |= punches.writeRegularizedPunchNoRecompute(r.getEmployeeId(), r.getRequestedCheckOut(),
                    r.getAttendanceDate(), r.getId()).isPresent();
        }
        if (wroteAny) {
            punches.recomputeForDate(r.getEmployeeId(), r.getAttendanceDate());
        }
        outbox.publish(AttendanceEvents.TOPIC, "regularization:" + requestId,
                AttendanceEvents.REGULARIZATION_APPROVED,
                new AttendanceEvents.RegularizationApproved(
                        requestId, r.getEmployeeId(), r.getAttendanceDate(), decision.actor().userId()));
        audit.emit("regularization_request", String.valueOf(requestId), "approved",
                null, Map.of("workDate", r.getAttendanceDate().toString()), true);
    }

    @Override
    public void onRejected(long requestId, String reason, ApprovalService.Decision decision) {
        RegularizationRequest r = require(requestId);
        r.setStatus(RegularizationStatus.REJECTED);
        outbox.publish(AttendanceEvents.TOPIC, "regularization:" + requestId,
                AttendanceEvents.REGULARIZATION_STAGE_REJECTED,
                new AttendanceEvents.RegularizationStageRejected(
                        requestId, r.getEmployeeId(),
                        decision.step().getStageNo(), decision.step().getLabel(),
                        decision.actor().userId(), reason));
        outbox.publish(AttendanceEvents.TOPIC, "regularization:" + requestId,
                AttendanceEvents.REGULARIZATION_REJECTED,
                new AttendanceEvents.RegularizationRejected(
                        requestId, r.getEmployeeId(), r.getAttendanceDate(),
                        decision.actor().userId(), reason));
        audit.emit("regularization_request", String.valueOf(requestId), "rejected",
                null, Map.of("reason", reason), true);
    }

    private RegularizationRequest require(long id) {
        return regularizations.findById(id)
                .orElseThrow(() -> new NotFoundError(AttendanceErrorCode.REGULARIZATION_NOT_FOUND,
                        "regularization request not found"));
    }

    private static AttendanceEvents.NextStage nextStage(ApprovalStep next) {
        if (next == null) return null;
        return new AttendanceEvents.NextStage(next.getStageNo(), next.getLabel(), next.getAssignedTo());
    }
}
