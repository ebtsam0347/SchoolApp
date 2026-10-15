package com.forward.hrms.attendance.approval;

import com.forward.hrms.attendance.entity.OvertimeRequest;
import com.forward.hrms.attendance.enums.AttendanceErrorCode;
import com.forward.hrms.attendance.enums.OvertimeStatus;
import com.forward.hrms.attendance.enums.RequestType;
import com.forward.hrms.attendance.events.AttendanceEvents;
import com.forward.hrms.attendance.repository.OvertimeRequestRepository;
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
 * Post-decision effects for OVERTIME approvals. Runs inside the
 * ApprovalService's MANDATORY transaction — changes commit together.
 */
@Component
@RequiredArgsConstructor
public class OvertimeApprovalOutcomeHandler implements ApprovalOutcomeHandler {

    private final OvertimeRequestRepository overtimes;
    private final PunchService punches;
    private final Outbox outbox;
    private final AuditEmitter audit;

    @Override
    public String requestType() {
        return RequestType.OVERTIME.name();
    }

    @Override
    public void onApproved(long requestId, ApprovalService.Decision decision) {
        OvertimeRequest ot = require(requestId);
        if (!decision.isFinal()) {
            outbox.publish(AttendanceEvents.TOPIC, "overtime:" + requestId,
                    AttendanceEvents.OVERTIME_STAGE_APPROVED,
                    new AttendanceEvents.OvertimeStageApproved(
                            requestId, ot.getEmployeeId(),
                            decision.step().getStageNo(), decision.step().getLabel(),
                            decision.actor().userId(), nextStage(decision.nextPending())));
            return;
        }
        ot.setStatus(OvertimeStatus.APPROVED);
        // Recompute the day's OT minute buckets — gates on APPROVED OT.
        punches.recomputeForDate(ot.getEmployeeId(), ot.getAttendanceDate());
        outbox.publish(AttendanceEvents.TOPIC, "overtime:" + requestId,
                AttendanceEvents.OVERTIME_APPROVED,
                new AttendanceEvents.OvertimeApproved(
                        requestId, ot.getEmployeeId(),
                        ot.getAttendanceDate(), decision.actor().userId()));
        audit.emit("overtime_request", String.valueOf(requestId), "approved",
                null, Map.of("workDate", ot.getAttendanceDate().toString()), true);
    }

    @Override
    public void onRejected(long requestId, String reason, ApprovalService.Decision decision) {
        OvertimeRequest ot = require(requestId);
        ot.setStatus(OvertimeStatus.REJECTED);
        outbox.publish(AttendanceEvents.TOPIC, "overtime:" + requestId,
                AttendanceEvents.OVERTIME_STAGE_REJECTED,
                new AttendanceEvents.OvertimeStageRejected(
                        requestId, ot.getEmployeeId(),
                        decision.step().getStageNo(), decision.step().getLabel(),
                        decision.actor().userId(), reason));
        outbox.publish(AttendanceEvents.TOPIC, "overtime:" + requestId,
                AttendanceEvents.OVERTIME_REJECTED,
                new AttendanceEvents.OvertimeRejected(
                        requestId, ot.getEmployeeId(), ot.getAttendanceDate(),
                        decision.actor().userId(), reason));
        audit.emit("overtime_request", String.valueOf(requestId), "rejected",
                null, Map.of("reason", reason), true);
    }

    private OvertimeRequest require(long id) {
        return overtimes.findById(id)
                .orElseThrow(() -> new NotFoundError(AttendanceErrorCode.OVERTIME_NOT_FOUND,
                        "overtime request not found"));
    }

    private static AttendanceEvents.NextStage nextStage(ApprovalStep next) {
        if (next == null) return null;
        return new AttendanceEvents.NextStage(next.getStageNo(), next.getLabel(), next.getAssignedTo());
    }
}
