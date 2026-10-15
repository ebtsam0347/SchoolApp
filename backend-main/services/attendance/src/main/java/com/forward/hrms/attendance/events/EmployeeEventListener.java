package com.forward.hrms.attendance.events;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.forward.hrms.attendance.entity.AttendanceEmployee;
import com.forward.hrms.attendance.entity.LeaveRequest;
import com.forward.hrms.attendance.entity.LeaveRequestDay;
import com.forward.hrms.attendance.entity.OvertimeRequest;
import com.forward.hrms.attendance.entity.RegularizationRequest;
import com.forward.hrms.attendance.entity.ShortLeaveRequest;
import com.forward.hrms.attendance.enums.AttendanceStatus;
import com.forward.hrms.attendance.enums.LeaveDayStatus;
import com.forward.hrms.attendance.enums.LeaveRequestStatus;
import com.forward.hrms.attendance.enums.OvertimeStatus;
import com.forward.hrms.attendance.enums.RegularizationStatus;
import com.forward.hrms.attendance.enums.RequestType;
import com.forward.hrms.attendance.enums.ShortLeaveStatus;
import com.forward.hrms.attendance.repository.AttendanceEmployeeRepository;
import com.forward.hrms.attendance.repository.AttendanceRecordRepository;
import com.forward.hrms.attendance.repository.LeaveRequestDayRepository;
import com.forward.hrms.attendance.repository.LeaveRequestRepository;
import com.forward.hrms.attendance.repository.OvertimeRequestRepository;
import com.forward.hrms.attendance.repository.RegularizationRequestRepository;
import com.forward.hrms.attendance.repository.ShortLeaveRequestRepository;
import com.forward.hrms.attendance.service.ShiftService;
import com.forward.hrms.commons.approval.service.ApprovalService;
import com.forward.hrms.attendance.service.DeviceSyncQueueService;
import com.forward.hrms.attendance.service.LeaveBalanceService;
import com.forward.hrms.commons.employeeevents.EmployeeEvents;
import com.forward.hrms.commons.employeeevents.EmployeeEventsConsumerSupport;

import lombok.extern.slf4j.Slf4j;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

/**
 * Reacts to employee-service events. All handlers are idempotent
 * because Kafka may redeliver:
 * <ul>
 *   <li>{@code employee.created} → provision balances for the current
 *       fiscal year.</li>
 *   <li>{@code employee.terminated} → cancel PENDING + post-LWD APPROVED
 *       leave (with balance unconsume + attendance row reset to ABSENT
 *       for post-LWD approved days), cancel post-LWD overtime and
 *       short-leave, cancel pending regularization. Days on/before LWD
 *       are honored as already taken. Then queue DELETE_USER on every
 *       active device the employee is enrolled on and drop the bridge
 *       rows so no further sync work targets the terminated employee.</li>
 *   <li>{@code employee.manager_changed} → reroute open MANAGER stages
 *       in {@code approval_steps} from old → new manager.</li>
 *   <li>{@code employee.reinstated} → HR rejected the final settlement
 *       that drove {@code employee.terminated} above; reopen the shift
 *       assignment closed on {@code lastWorkingDay} and flip the local
 *       employee mirror back to ACTIVE. Leave/overtime/short-leave/
 *       regularization cancellations and device DELETE_USER queuing are
 *       not undone — those are terminal actions, not date-ranged rows.</li>
 * </ul>
 */
@Slf4j
@Component
public class EmployeeEventListener extends EmployeeEventsConsumerSupport {

    private final LeaveBalanceService balances;
    private final LeaveRequestRepository leaves;
    private final LeaveRequestDayRepository leaveDays;
    private final OvertimeRequestRepository overtimes;
    private final ShortLeaveRequestRepository shortLeaves;
    private final RegularizationRequestRepository regularizations;
    private final AttendanceRecordRepository attendanceRecords;
    private final ApprovalService approvals;
    private final DeviceSyncQueueService deviceSync;
    private final AttendanceEmployeeRepository attendanceEmployees;
    private final ShiftService shiftService;

    public EmployeeEventListener(LeaveBalanceService balances,
                                 LeaveRequestRepository leaves,
                                 LeaveRequestDayRepository leaveDays,
                                 OvertimeRequestRepository overtimes,
                                 ShortLeaveRequestRepository shortLeaves,
                                 RegularizationRequestRepository regularizations,
                                 AttendanceRecordRepository attendanceRecords,
                                 ApprovalService approvals,
                                 DeviceSyncQueueService deviceSync,
                                 AttendanceEmployeeRepository attendanceEmployees,
                                 ShiftService shiftService,
                                 @Qualifier("outboxObjectMapper") ObjectMapper json) {
        super(json);
        this.balances = balances;
        this.leaves = leaves;
        this.leaveDays = leaveDays;
        this.overtimes = overtimes;
        this.shortLeaves = shortLeaves;
        this.regularizations = regularizations;
        this.attendanceRecords = attendanceRecords;
        this.approvals = approvals;
        this.deviceSync = deviceSync;
        this.attendanceEmployees = attendanceEmployees;
        this.shiftService = shiftService;
    }

    @KafkaListener(topics = EmployeeEvents.TOPIC, groupId = "attendance.employee-events")
    @Transactional
    public void onEmployeeEvent(String message) throws java.io.IOException {
        dispatch(message);
    }

    @Override
    protected void handle(String eventType, JsonNode payload) {
        switch (eventType) {
            case EmployeeEvents.EMPLOYEE_CREATED -> handleCreated(payload);
            case EmployeeEvents.EMPLOYEE_UPDATED -> handleUpdated(payload);
            case EmployeeEvents.EMPLOYEE_TERMINATED -> handleTerminated(payload);
            case EmployeeEvents.EMPLOYEE_REINSTATED -> handleReinstated(payload);
            case EmployeeEvents.EMPLOYEE_MANAGER_CHANGED -> handleManagerChanged(payload);
            default -> log.debug("Ignoring employee event type {}", eventType);
        }
    }

    private void handleCreated(JsonNode p) {
        long employeeId = p.path("employeeId").asLong();
        if (employeeId == 0) return;
        LocalDate joiningDate = readDate(p, "joiningDate");
        upsertEmployeeSnapshot(employeeId,
                textOrEmpty(p, "employeeCode"),
                textOrEmpty(p, "fullName"),
                joiningDate);
        balances.provisionOnHire(employeeId, joiningDate);
    }

    /**
     * Track changes to {@code employeeCode} / {@code fullName} /
     * {@code employmentStatus} on the attendance-side employee snapshot.
     * The {@code employee.updated} payload carries only the fields that
     * actually changed; missing keys are no-ops here. employee-service
     * emits {@code changes.employmentStatus} on every lifecycle transition
     * (suspend / lift / resign / withdraw) so the nightly close's
     * active-employee selection stays accurate without an HTTP call.
     */
    private void handleUpdated(JsonNode p) {
        long employeeId = p.path("employeeId").asLong();
        if (employeeId == 0) return;
        JsonNode changes = p.path("changes");
        if (!changes.isObject()) return;
        boolean hasCode = changes.has("employeeCode");
        boolean hasName = changes.has("fullName");
        boolean hasStatus = changes.has("employmentStatus");
        if (!hasCode && !hasName && !hasStatus) return;

        AttendanceEmployee row = attendanceEmployees.findByEmployeeId(employeeId).orElse(null);
        if (row == null) {
            // Snapshot row missing — out-of-order delivery before
            // employee.created landed. Insert what we know; the next
            // employee.updated for any other field will fill the rest.
            // employmentStatus defaults to ACTIVE on the entity.
            row = new AttendanceEmployee();
            row.setEmployeeId(employeeId);
            row.setEmployeeCode(textOrEmpty(changes, "employeeCode"));
            row.setFullName(textOrEmpty(changes, "fullName"));
            if (hasStatus) row.setEmploymentStatus(textOrEmpty(changes, "employmentStatus"));
            attendanceEmployees.save(row);
            return;
        }
        if (hasCode) row.setEmployeeCode(textOrEmpty(changes, "employeeCode"));
        if (hasName) row.setFullName(textOrEmpty(changes, "fullName"));
        if (hasStatus) row.setEmploymentStatus(textOrEmpty(changes, "employmentStatus"));
    }

    private void upsertEmployeeSnapshot(long employeeId, String employeeCode, String fullName,
                                        LocalDate joiningDate) {
        if (employeeCode.isBlank() || fullName.isBlank()) return;
        AttendanceEmployee row = attendanceEmployees.findByEmployeeId(employeeId)
                .orElseGet(() -> {
                    AttendanceEmployee r = new AttendanceEmployee();
                    r.setEmployeeId(employeeId);
                    return r;
                });
        row.setEmployeeCode(employeeCode);
        row.setFullName(fullName);
        if (joiningDate != null) row.setJoiningDate(joiningDate);
        attendanceEmployees.save(row);
    }

    private static String textOrEmpty(JsonNode node, String field) {
        JsonNode v = node.path(field);
        return v.isMissingNode() || v.isNull() ? "" : v.asText();
    }

    private void handleTerminated(JsonNode p) {
        long employeeId = p.path("employeeId").asLong();
        if (employeeId == 0) return;
        LocalDate today = LocalDate.now();
        LocalDate lwd = readDate(p, "lastWorkingDay");
        if (lwd == null) lwd = today;

        cancelLeaves(employeeId, lwd);
        cancelOvertimes(employeeId, lwd);
        cancelShortLeaves(employeeId, lwd);
        cancelRegularizations(employeeId);
        log.info("Reached to employee shift statement");
        shiftService.closeEmployeeShift(employeeId,lwd);
        log.info("Next line to employee shift statement");

        deviceSync.queueDeleteForAllDevices(employeeId);
        // Mark the local mirror TERMINATED so the nightly close stops
        // selecting this employee for absent-marking.
        attendanceEmployees.findByEmployeeId(employeeId)
                .ifPresent(row -> row.setEmploymentStatus("TERMINATED"));
    }

    /**
     * HR rejected a final settlement — {@code TerminationSaga.undoTermination}
     * restored the employee on the employee-service side; reopen the
     * shift assignment {@code handleTerminated} closed and restore the
     * local employee mirror so the nightly close selects the employee
     * again. Idempotent — {@code reopenEmployeeShift} no-ops if nothing
     * was closed on {@code lastWorkingDay} or if already reopened.
     */
    private void handleReinstated(JsonNode p) {
        long employeeId = p.path("employeeId").asLong();
        if (employeeId == 0) return;
        LocalDate lwd = readDate(p, "lastWorkingDay");
        if (lwd == null) {
            log.warn("Skipping employee.reinstated without lastWorkingDay for {}", employeeId);
            return;
        }

        shiftService.reopenEmployeeShift(employeeId, lwd);

        attendanceEmployees.findByEmployeeId(employeeId)
                .ifPresent(row -> row.setEmploymentStatus("ACTIVE"));
    }

    /**
     * Cancel every PENDING leave entirely. For APPROVED/PARTIALLY_APPROVED
     * leaves, flip post-LWD days that were APPROVED back to REJECTED:
     * unconsume the consumed balance, reset the
     * {@code attendance_records} row to ABSENT, and recompute parent
     * status. PENDING days that fall after LWD are released. Days
     * on/before LWD are honored as already taken.
     */
    private void cancelLeaves(long employeeId, LocalDate lwd) {
        List<LeaveRequest> active = leaves.findActiveOverlapping(employeeId,
                LocalDate.now().minusYears(1),
                LocalDate.now().plusYears(5));
        for (LeaveRequest lr : active) {
            if (lr.getStatus() == LeaveRequestStatus.PENDING) {
                BigDecimal heldDays = sumPending(lr.getId(), lr);
                for (LeaveRequestDay d : leaveDays.findAllByLeaveRequestIdAndStatus(
                        lr.getId(), LeaveDayStatus.PENDING)) {
                    d.setStatus(LeaveDayStatus.REJECTED);
                }

                if (heldDays.signum() > 0) {
                    balances.release(employeeId, lr.getLeaveTypeId(),
                            lr.getFromDate(), heldDays);
                }
                approvals.cancelChain(RequestType.LEAVE.name(), lr.getId());
                lr.setStatus(LeaveRequestStatus.CANCELLED);
                continue;
            }
            BigDecimal unconsume = BigDecimal.ZERO;
            BigDecimal release = BigDecimal.ZERO;
            // Derive per-day portion from the request's total days divided by
            // its day-row count. This stays correct even if a row has the
            // legacy halfDay flag set on a multi-day request — `lr.getDays()`
            // is the authoritative consumption (LeaveService.computeDays).
            // Per-day was previously `lr.isHalfDay() ? 0.5 : 1.0`, which
            // double-credits when halfDay=true and size>1.
            List<LeaveRequestDay> dayRows = leaveDays.findAllByLeaveRequestIdOrderByAttendanceDateAsc(lr.getId());
            BigDecimal portion = dayRows.isEmpty()
                    ? BigDecimal.ZERO
                    : lr.getDays().divide(BigDecimal.valueOf(dayRows.size()),
                            2, java.math.RoundingMode.HALF_UP);
            for (LeaveRequestDay d : dayRows) {
                if (!d.getAttendanceDate().isAfter(lwd)) continue;
                if (d.getStatus() == LeaveDayStatus.APPROVED) {
                    d.setStatus(LeaveDayStatus.REJECTED);
                    unconsume = unconsume.add(portion);
                    attendanceRecords.findByEmployeeIdAndAttendanceDate(employeeId, d.getAttendanceDate())
                            .ifPresent(rec -> rec.setStatus(AttendanceStatus.ABSENT));
                } else if (d.getStatus() == LeaveDayStatus.PENDING) {
                    d.setStatus(LeaveDayStatus.REJECTED);
                    release = release.add(portion);
                }
            }
            if (unconsume.signum() > 0) {
                balances.unconsume(employeeId, lr.getLeaveTypeId(),
                        lr.getFromDate(), unconsume);
            }
            if (release.signum() > 0) {
                balances.release(employeeId, lr.getLeaveTypeId(),
                        lr.getFromDate(), release);
            }
            // Roll up parent status: any APPROVED day still on the books?
            long approvedRemaining = leaveDays.findAllByLeaveRequestIdAndStatus(
                    lr.getId(), LeaveDayStatus.APPROVED).size();
            if (approvedRemaining == 0) {
                lr.setStatus(LeaveRequestStatus.CANCELLED);
            }
        }
    }

    private void cancelOvertimes(long employeeId, LocalDate lwd) {
        for (OvertimeRequest o : overtimes.findActiveOnOrAfter(employeeId, lwd.plusDays(1))) {
            o.setStatus(OvertimeStatus.CANCELLED);
            approvals.cancelChain(RequestType.OVERTIME.name(), o.getId());
        }
    }

    private void cancelShortLeaves(long employeeId, LocalDate lwd) {
        for (ShortLeaveRequest s : shortLeaves.findActiveOnOrAfter(employeeId, lwd.plusDays(1))) {
            s.setStatus(ShortLeaveStatus.CANCELLED);
            approvals.cancelChain(RequestType.SHORT_LEAVE.name(), s.getId());
        }
    }

    private void cancelRegularizations(long employeeId) {
        for (RegularizationRequest r : regularizations.findAllByEmployeeIdAndStatus(
                employeeId, RegularizationStatus.PENDING)) {
            r.setStatus(RegularizationStatus.CANCELLED);
            approvals.cancelChain(RequestType.REGULARIZATION.name(), r.getId());
        }
    }

    private BigDecimal sumPending(long leaveRequestId, LeaveRequest lr) {
        long count = leaveDays.findAllByLeaveRequestIdAndStatus(leaveRequestId, LeaveDayStatus.PENDING).size();
        if (lr.isHalfDay() && count == 1) return new BigDecimal("0.5");
        return BigDecimal.valueOf(count);
    }

    private void handleManagerChanged(JsonNode p) {
        Long oldManagerId = readLong(p, "oldManagerId");
        Long newManagerId = readLong(p, "newManagerId");
        if (oldManagerId == null) return;
        // rerouteManager works on approval_steps and is type-agnostic
        // (assigned_to is a user id, not a request-type-scoped value),
        // so one call covers leave / overtime / regularization /
        // short-leave together.
        int rerouted = approvals.rerouteManager(oldManagerId, newManagerId);
        if (rerouted > 0) {
            log.info("Rerouted {} open manager-stage rows {} → {}",
                    rerouted, oldManagerId, newManagerId);
        }
    }
}
