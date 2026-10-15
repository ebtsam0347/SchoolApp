package com.forward.hrms.attendance.events;

import com.forward.hrms.commons.kafka.event.KafkaTopics;

import java.time.LocalDateTime;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.UUID;

/**
 * Event-type strings + the attendance events topic in one place. Keep
 * names stable across deployments — they're part of the cross-service
 * contract that payroll / notification / reports depend on.
 */
public final class AttendanceEvents {

    public static final String SERVICE_NAME = "attendance";
    public static final String TOPIC = KafkaTopics.events(SERVICE_NAME);

    public static final String PUNCH_RECORDED        = "attendance.punch_recorded";
    public static final String DAILY_CLOSED          = "attendance.daily_closed";
    public static final String SHIFT_OVERRIDDEN      = "attendance.shift_overridden";

    public static final String LEAVE_APPLIED         = "leave.applied";
    public static final String LEAVE_STAGE_APPROVED  = "leave.stage_approved";
    public static final String LEAVE_STAGE_REJECTED  = "leave.stage_rejected";
    public static final String LEAVE_DAY_APPROVED    = "leave.day_approved";
    public static final String LEAVE_DAY_REJECTED    = "leave.day_rejected";
    public static final String LEAVE_REQUEST_FINALIZED = "leave.request_finalized";
    public static final String LEAVE_CANCELLED       = "leave.cancelled";
    public static final String LEAVE_ENCASHED        = "leave.encashed";

    public static final String SHORT_LEAVE_REQUESTED        = "short_leave.requested";
    public static final String SHORT_LEAVE_STAGE_APPROVED   = "short_leave.stage_approved";
    public static final String SHORT_LEAVE_STAGE_REJECTED   = "short_leave.stage_rejected";
    public static final String SHORT_LEAVE_APPROVED         = "short_leave.approved";
    public static final String SHORT_LEAVE_REJECTED         = "short_leave.rejected";
    public static final String SHORT_LEAVE_CANCELLED        = "short_leave.cancelled";

    public static final String OVERTIME_REQUESTED       = "overtime.requested";
    public static final String OVERTIME_STAGE_APPROVED  = "overtime.stage_approved";
    public static final String OVERTIME_STAGE_REJECTED  = "overtime.stage_rejected";
    public static final String OVERTIME_APPROVED        = "overtime.approved";
    public static final String OVERTIME_REJECTED        = "overtime.rejected";
    public static final String OVERTIME_CANCELLED       = "overtime.cancelled";

    public static final String REGULARIZATION_STAGE_APPROVED = "regularization.stage_approved";
    public static final String REGULARIZATION_STAGE_REJECTED = "regularization.stage_rejected";
    public static final String REGULARIZATION_APPROVED       = "regularization.approved";
    public static final String REGULARIZATION_REJECTED       = "regularization.rejected";
    public static final String REGULARIZATION_CANCELLED      = "regularization.cancelled";

    public static final String PERIOD_LOCKED   = "attendance.period_locked";
    public static final String PERIOD_UNLOCKED = "attendance.period_unlocked";

    private AttendanceEvents() {}

    public record StageSnapshot(int stageNo, String stageRole, Long assignedTo) {}

    public record NextStage(int stageNo, String stageRole, Long assignedTo) {}

    public record PunchRecorded(
            long employeeId, LocalDateTime punchedAt, LocalDate workDate, String source, String reason) {}

    public record DailyClosed(LocalDate workDate, int absentMarked, int rowsTouched) {}

    public record ShiftOverridden(
            long employeeId, LocalDate fromDate, LocalDate toDate,
            LocalTime shiftStartTime, LocalTime shiftEndTime,
            LocalTime shiftBreakStartTime, LocalTime shiftBreakEndTime,
            int shiftGraceMinutes, String reason, String op) {}

    public record LeaveApplied(
            long requestId, long employeeId, String leaveTypeCode,
            LocalDate fromDate, LocalDate toDate, double days,
            List<LocalDate> workDates, List<StageSnapshot> stages) {}

    public record LeaveStageApproved(
            long requestId, long employeeId, int stageNo, String stageRole, Long by, NextStage nextStage) {}

    public record LeaveStageRejected(
            long requestId, long employeeId, int stageNo, String stageRole, Long by, String reason) {}

    public record LeaveDayApproved(
            long requestId, long employeeId, String leaveTypeCode,
            LocalDate workDate, Long by, int decidedAtStage) {}

    public record LeaveDayRejected(
            long requestId, long employeeId, LocalDate workDate, Long by, int decidedAtStage, String reason) {}

    public record LeaveRequestFinalized(
            long requestId, long employeeId, String finalStatus,
            List<LocalDate> approvedDates, List<LocalDate> rejectedDates) {}

    public record LeaveCancelled(long requestId, long employeeId, String reason) {}

    /**
     * Emitted when an encashable leave cycle closes at renewal with unused
     * balance. Carries days only — payroll values the payout (per-day
     * salary on a 26-day basis). {@code cycleStart}/{@code cycleEnd} scope
     * the closed cycle.
     */
    public record LeaveEncashed(
            long employeeId, String leaveTypeCode, java.math.BigDecimal days,
            LocalDate cycleStart, LocalDate cycleEnd) {}

    public record ShortLeaveRequested(
            long requestId, long employeeId, LocalDate leaveDate,
            LocalTime startTime, LocalTime endTime, int totalMinutes,
            List<StageSnapshot> stages) {}

    public record ShortLeaveStageApproved(
            long requestId, long employeeId, int stageNo, String stageRole, Long by, NextStage nextStage) {}

    public record ShortLeaveStageRejected(
            long requestId, long employeeId, int stageNo, String stageRole, Long by, String reason) {}

    public record ShortLeaveApproved(
            long requestId, long employeeId, LocalDate leaveDate,
            LocalTime startTime, LocalTime endTime, int totalMinutes, Long by) {}

    public record ShortLeaveRejected(
            long requestId, long employeeId, LocalDate leaveDate, Long by, String reason) {}

    public record ShortLeaveCancelled(long requestId, long employeeId, LocalDate leaveDate) {}

    public record OvertimeRequested(
            long requestId, long employeeId, LocalDate workDate, UUID bulkBatchId,
            List<StageSnapshot> stages) {}

    public record OvertimeStageApproved(
            long requestId, long employeeId, int stageNo, String stageRole, Long by, NextStage nextStage) {}

    public record OvertimeStageRejected(
            long requestId, long employeeId, int stageNo, String stageRole, Long by, String reason) {}

    public record OvertimeApproved(long requestId, long employeeId, LocalDate workDate, Long by) {}

    public record OvertimeRejected(long requestId, long employeeId, LocalDate workDate, Long by, String reason) {}

    public record OvertimeCancelled(long requestId, long employeeId, LocalDate workDate) {}

    public record RegularizationStageApproved(
            long requestId, long employeeId, int stageNo, String stageRole, Long by, NextStage nextStage) {}

    public record RegularizationStageRejected(
            long requestId, long employeeId, int stageNo, String stageRole, Long by, String reason) {}

    public record RegularizationApproved(
            long requestId, long employeeId, LocalDate workDate, Long by) {}

    public record RegularizationRejected(
            long requestId, long employeeId, LocalDate workDate, Long by, String reason) {}

    public record RegularizationCancelled(long requestId, long employeeId, LocalDate workDate) {}

    public record PeriodLocked(int periodYear, int periodMonth, String lockedBy, LocalDateTime lockedAt) {}

    public record PeriodUnlocked(int periodYear, int periodMonth, String unlockedBy, LocalDateTime unlockedAt) {}
}
