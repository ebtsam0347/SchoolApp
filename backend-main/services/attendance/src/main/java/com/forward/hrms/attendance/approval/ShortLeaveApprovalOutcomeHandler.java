package com.forward.hrms.attendance.approval;

import com.forward.hrms.attendance.entity.ShortLeaveRequest;
import com.forward.hrms.attendance.enums.AttendanceErrorCode;
import com.forward.hrms.attendance.enums.RequestType;
import com.forward.hrms.attendance.enums.ShortLeaveStatus;
import com.forward.hrms.attendance.events.AttendanceEvents;
import com.forward.hrms.attendance.repository.ShortLeaveRequestRepository;
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
 * Post-decision effects for SHORT_LEAVE approvals. Runs inside the
 * ApprovalService's MANDATORY transaction — changes commit together.
 */
@Component
@RequiredArgsConstructor
public class ShortLeaveApprovalOutcomeHandler implements ApprovalOutcomeHandler {

    private final ShortLeaveRequestRepository shortLeaves;
    private final Outbox outbox;
    private final AuditEmitter audit;

    @Override
    public String requestType() {
        return RequestType.SHORT_LEAVE.name();
    }

    @Override
    public void onApproved(long requestId, ApprovalService.Decision decision) {
        ShortLeaveRequest sl = require(requestId);
        if (!decision.isFinal()) {
            outbox.publish(AttendanceEvents.TOPIC, "short_leave:" + requestId,
                    AttendanceEvents.SHORT_LEAVE_STAGE_APPROVED,
                    new AttendanceEvents.ShortLeaveStageApproved(
                            requestId, sl.getEmployeeId(),
                            decision.step().getStageNo(), decision.step().getLabel(),
                            decision.actor().userId(), nextStage(decision.nextPending())));
            return;
        }
        sl.setStatus(ShortLeaveStatus.APPROVED);
        outbox.publish(AttendanceEvents.TOPIC, "short_leave:" + requestId,
                AttendanceEvents.SHORT_LEAVE_APPROVED,
                new AttendanceEvents.ShortLeaveApproved(
                        requestId, sl.getEmployeeId(), sl.getLeaveDate(),
                        sl.getStartTime(), sl.getEndTime(), sl.getTotalMinutes(),
                        decision.actor().userId()));
        audit.emit("short_leave_request", String.valueOf(requestId), "approved",
                null, Map.of("leaveDate", sl.getLeaveDate().toString()), true);
    }

    @Override
    public void onRejected(long requestId, String reason, ApprovalService.Decision decision) {
        ShortLeaveRequest sl = require(requestId);
        sl.setStatus(ShortLeaveStatus.REJECTED);
        outbox.publish(AttendanceEvents.TOPIC, "short_leave:" + requestId,
                AttendanceEvents.SHORT_LEAVE_STAGE_REJECTED,
                new AttendanceEvents.ShortLeaveStageRejected(
                        requestId, sl.getEmployeeId(),
                        decision.step().getStageNo(), decision.step().getLabel(),
                        decision.actor().userId(), reason));
        outbox.publish(AttendanceEvents.TOPIC, "short_leave:" + requestId,
                AttendanceEvents.SHORT_LEAVE_REJECTED,
                new AttendanceEvents.ShortLeaveRejected(
                        requestId, sl.getEmployeeId(), sl.getLeaveDate(),
                        decision.actor().userId(), reason));
        audit.emit("short_leave_request", String.valueOf(requestId), "rejected",
                null, Map.of("reason", reason), true);
    }

    private ShortLeaveRequest require(long id) {
        return shortLeaves.findById(id)
                .orElseThrow(() -> new NotFoundError(AttendanceErrorCode.SHORT_LEAVE_NOT_FOUND,
                        "short leave not found"));
    }

    private static AttendanceEvents.NextStage nextStage(ApprovalStep next) {
        if (next == null) return null;
        return new AttendanceEvents.NextStage(next.getStageNo(), next.getLabel(), next.getAssignedTo());
    }
}
