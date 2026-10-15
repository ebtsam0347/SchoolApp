package com.forward.hrms.attendance.service;

import com.forward.hrms.attendance.enums.RequestType;
import com.forward.hrms.attendance.events.AttendanceEvents;
import com.forward.hrms.commons.kafka.outbox.Outbox;

import lombok.RequiredArgsConstructor;

import org.springframework.stereotype.Service;

/**
 * One-stop publisher for the per-type stage_approved / stage_rejected
 * outbox events. The outbox payload differs per request type, but the
 * call shape is identical — caller passes identity + stage info, the
 * publisher picks the right event-name constant and concrete record.
 *
 * Audit events stay inline at the call site; their action label and
 * extras vary too much per context (final approve vs. mid-stage,
 * cancel, reject) to fold in here without losing clarity.
 */
@Service
@RequiredArgsConstructor
public class ApprovalEventPublisher {

    private final Outbox outbox;

    public void stageApproved(RequestType type, long requestId, long employeeId,
                              int stageNo, String stageLabel, Long byUserId,
                              AttendanceEvents.NextStage nextStage) {
        switch (type) {
            case LEAVE -> outbox.publish(AttendanceEvents.TOPIC, "leave:" + requestId,
                    AttendanceEvents.LEAVE_STAGE_APPROVED,
                    new AttendanceEvents.LeaveStageApproved(
                            requestId, employeeId, stageNo, stageLabel, byUserId, nextStage));
            case OVERTIME -> outbox.publish(AttendanceEvents.TOPIC, "overtime:" + requestId,
                    AttendanceEvents.OVERTIME_STAGE_APPROVED,
                    new AttendanceEvents.OvertimeStageApproved(
                            requestId, employeeId, stageNo, stageLabel, byUserId, nextStage));
            case REGULARIZATION -> outbox.publish(AttendanceEvents.TOPIC, "regularization:" + requestId,
                    AttendanceEvents.REGULARIZATION_STAGE_APPROVED,
                    new AttendanceEvents.RegularizationStageApproved(
                            requestId, employeeId, stageNo, stageLabel, byUserId, nextStage));
            case SHORT_LEAVE -> outbox.publish(AttendanceEvents.TOPIC, "short_leave:" + requestId,
                    AttendanceEvents.SHORT_LEAVE_STAGE_APPROVED,
                    new AttendanceEvents.ShortLeaveStageApproved(
                            requestId, employeeId, stageNo, stageLabel, byUserId, nextStage));
        }
    }

    public void stageRejected(RequestType type, long requestId, long employeeId,
                              int stageNo, String stageLabel, Long byUserId,
                              String reason) {
        switch (type) {
            case LEAVE -> outbox.publish(AttendanceEvents.TOPIC, "leave:" + requestId,
                    AttendanceEvents.LEAVE_STAGE_REJECTED,
                    new AttendanceEvents.LeaveStageRejected(
                            requestId, employeeId, stageNo, stageLabel, byUserId, reason));
            case OVERTIME -> outbox.publish(AttendanceEvents.TOPIC, "overtime:" + requestId,
                    AttendanceEvents.OVERTIME_STAGE_REJECTED,
                    new AttendanceEvents.OvertimeStageRejected(
                            requestId, employeeId, stageNo, stageLabel, byUserId, reason));
            case REGULARIZATION -> outbox.publish(AttendanceEvents.TOPIC, "regularization:" + requestId,
                    AttendanceEvents.REGULARIZATION_STAGE_REJECTED,
                    new AttendanceEvents.RegularizationStageRejected(
                            requestId, employeeId, stageNo, stageLabel, byUserId, reason));
            case SHORT_LEAVE -> outbox.publish(AttendanceEvents.TOPIC, "short_leave:" + requestId,
                    AttendanceEvents.SHORT_LEAVE_STAGE_REJECTED,
                    new AttendanceEvents.ShortLeaveStageRejected(
                            requestId, employeeId, stageNo, stageLabel, byUserId, reason));
        }
    }
}
