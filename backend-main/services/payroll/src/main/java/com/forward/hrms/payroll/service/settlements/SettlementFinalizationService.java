package com.forward.hrms.payroll.service.settlements;

import com.forward.hrms.commons.core.context.CurrentUser;
import com.forward.hrms.commons.core.context.CurrentUserHolder;
import com.forward.hrms.commons.core.error.ConflictError;
import com.forward.hrms.commons.core.error.NotFoundError;
import com.forward.hrms.commons.core.error.UnauthorizedError;
import com.forward.hrms.commons.core.money.Money;
import com.forward.hrms.commons.kafka.outbox.AuditEmitter;
import com.forward.hrms.commons.kafka.outbox.Outbox;
import com.forward.hrms.employee.dto.response.BelongingClearanceCheckDto;
import com.forward.hrms.payroll.client.EmployeeClient;
import com.forward.hrms.payroll.client.dto.attendace.AttendanceSummaryDto;
import com.forward.hrms.payroll.dto.request.ApprovalRequest;
import com.forward.hrms.payroll.dto.request.CancelSettlementRequest;
import com.forward.hrms.payroll.dto.request.CreateFinalSettlementRequest;
import com.forward.hrms.payroll.dto.request.SettlementPaymentRequest;
import com.forward.hrms.payroll.dto.response.FinalSettlementDto;
import com.forward.hrms.payroll.dto.response.PaymentResultDto;
import com.forward.hrms.payroll.dto.response.SettlementPreviewDto;
import com.forward.hrms.payroll.entity.*;
import com.forward.hrms.payroll.enums.AdvanceStatus;
import com.forward.hrms.payroll.enums.EmployeeFinalSettlementStatus;
import com.forward.hrms.payroll.enums.PayrollErrorCode;
import com.forward.hrms.payroll.enums.SettlementOvertimeType;
import com.forward.hrms.payroll.events.PayrollEvents;
import com.forward.hrms.payroll.repository.EmployeeFinalSettlementRepository;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class SettlementFinalizationService {

    // Same Normal OT policy constants used by SettlementServiceNew's amount
    // calculation — kept here only to reconstruct the audit-trail inputs
    // (per-day/per-hour salary), never to recompute the amounts themselves.
    private static final BigDecimal NORMAL_OT_DAYS_PER_MONTH = BigDecimal.valueOf(26);
    private static final BigDecimal NORMAL_OT_HOURS_PER_DAY = BigDecimal.valueOf(8);
    private static final BigDecimal NORMAL_OT_MULTIPLIER = BigDecimal.valueOf(2);

    private final SettlementServiceNew previewService;

    private final EmployeeFinalSettlementRepository settlementRepository;
    // we removed child repositories for now because cascade is enabled on the parent entity,
    // so it means saving the parent will save the children automatically.

    private final AuditEmitter audit;
    private final FinalSettlementWorkflowValidatorService workflowValidator;

    private final FinalSettlementPaymentService paymentService;

    // OutBox Service to publish the event
    private final Outbox outbox;

    private final EmployeeClient employeeClient;


    @Transactional
    public FinalSettlementDto create(CreateFinalSettlementRequest request) {

        SettlementPreviewDto preview = previewService.previewNew(
                request.employeeId(),
                request.lastWorkingDay(),
                request.settlementAdjustmentType(),
                request.noticePayPercentage(),
                request.forcedDeductionPercentage(),
                request.bonusRate()
        );

        workflowValidator.canCreate(request.employeeId(), request.lastWorkingDay());

        EmployeeFinalSettlement settlement = buildSettlement(request, preview);

        addLeave(preview, settlement);
        addBonuses(preview, settlement);
        addPermanentAdvances(preview, settlement);
        addDeductions(preview, settlement);
        addAttendanceSnapshot(preview, settlement);
        addOvertimeSnapshot(preview, settlement);

        settlement = settlementRepository.save(settlement);

        audit.emit(
                "employee_final_settlement",
                settlement.getId().toString(),
                "submitted",
                null,
                Map.of(
                        "employeeId", settlement.getEmployeeId(),
                        "net", settlement.getNetSettlement(),
                        "createdBy", settlement.getCreatedBy()
                ),
                true,
                "Final settlement submitted"
        );

        /*
         * --------------------------------------------------------
         * HR policy: every closing happens at settlement CREATION,
         * not at PAID.
         * --------------------------------------------------------
         *
         * Leave encashment, bonus settlement, permanent/short-term
         * advance recovery (closes the advance), fair price shop
         * recovery, and outstanding deductions are all finalized here.
         * markPaid() only records the actual payment confirmation
         * (payment date/method/reference) afterward — it does not
         * repeat this processing.
         */
        PaymentResultDto closingResult = paymentService.process(settlement);

        audit.emit(
                "employee_final_settlement",
                settlement.getId().toString(),
                "closed",
                null,
                Map.of(
                        "employeeId", settlement.getEmployeeId(),
                        "leavePaid", closingResult.getLeavePaid(),
                        "bonusPaid", closingResult.getBonusPaid(),
                        "advanceRecovered", closingResult.getAdvanceRecovered(),
                        "fairPriceRecovered", closingResult.getFairPriceRecovered(),
                        "deductionsRecovered", closingResult.getDeductionsRecovered(),
                        "netSettlement", closingResult.getNetPaid()
                ),
                true,
                "Leave, bonus, advances and deductions closed at settlement creation"
        );

        outbox.publish(
                PayrollEvents.TOPIC,
                "final_settlement:" + settlement.getId(),
                PayrollEvents.FINAL_SETTLEMENT_CREATED,

                new PayrollEvents.FinalSettlementCreated(
                        settlement.getId(),
                        settlement.getEmployeeId(),
                        settlement.getEmployeeCode(),
                        settlement.getEmployeeName(),
                        settlement.getLastWorkingDay(),
                        settlement.getRemarks(),
                        settlement.getNetSettlement(),
                        //LocalDate.now(),
                        settlement.getCreatedAt().toLocalDate(),
                        //currentUser().userId()
                        settlement.getCreatedBy()
                )
        );

        return FinalSettlementDto.from(settlement);
    }


    private EmployeeFinalSettlement buildSettlement(
            CreateFinalSettlementRequest request,
            SettlementPreviewDto preview) {

        CurrentUser user = currentUser();
        // get current DateTime
        LocalDateTime currentDateTime = LocalDateTime.now();


        EmployeeFinalSettlement settlement = new EmployeeFinalSettlement();
        settlement.setEmployeeId(preview.employee().employeeId());
        settlement.setEmployeeCode(preview.employee().employeeCode());
        settlement.setEmployeeName(preview.employee().employeeName());
        settlement.setDepartment(preview.employee().department());
        settlement.setSection(preview.employee().section());
        settlement.setDesignation(preview.employee().designation());
        settlement.setGrade(preview.employee().grade());
        settlement.setGradeType(preview.employee().gradeType());
        settlement.setHireDate(preview.employee().hireDate());
        settlement.setGratuityStartDate(preview.employee().gratuityStartDate());
        settlement.setLastWorkingDay(preview.lastWorkingDay());
        settlement.setMonthlyBasicSalary(preview.earnedSalary().monthlyBasicSalary());
        settlement.setEarnedBasicSalary(preview.earnedSalary().earnedBasic());
        settlement.setAllowances(preview.earnedSalary().allowances());
        settlement.setGrossSalary(preview.earnedSalary().gross());
        settlement.setGratuityEligible(preview.gratuity().eligible());
        settlement.setGratuityCompletedYears(preview.gratuity().completedYears());
        settlement.setGratuityAmount(preview.gratuity().amount());
        settlement.setPermanentAdvance(preview.advances().permanent());
        settlement.setShortTermAdvance(preview.advances().shortTerm());
        settlement.setRfdAdvance(preview.advances().rfd());
        settlement.setTotalAdvance(preview.advances().total());
        settlement.setOutstandingBonus(preview.bonus().outstanding());
        settlement.setFairPriceShopAmount(preview.fairPriceShop().outstandingAmount());
        settlement.setCanteenAmount(preview.canteen().amount());
        settlement.setGrossSettlement(preview.grossSettlement());
        settlement.setTotalDeductions(preview.totalDeductions());
        settlement.setTaxAmount(preview.tax());
        settlement.setNetSettlement(preview.net());
        settlement.setRemarks(request.remarks());

        if (preview.annualExcessiveLeave() != null) {
            settlement.setAnnualExcessiveLeaveDays(preview.annualExcessiveLeave().excessiveDays());
            settlement.setAnnualExcessiveLeaveAmount(preview.annualExcessiveLeave().excessiveAmount());
        }

        if (preview.medicalAndCasualExcessiveLeave() != null) {
            settlement.setMedicalCasualExcessiveLeaveDays(preview.medicalAndCasualExcessiveLeave().excessiveDays());
            settlement.setMedicalCasualExcessiveLeaveAmount(preview.medicalAndCasualExcessiveLeave().excessiveAmount());
        }

        settlement.setStatus(EmployeeFinalSettlementStatus.DRAFT);
        settlement.setCreatedAt(currentDateTime);
        settlement.setUpdatedAt(currentDateTime);
        settlement.setCreatedBy(user.userId());

        // Adjustments Policy Fields
        settlement.setAdjustmentType(preview.terminationPolicy().adjustmentType());
        settlement.setNoticePayPercentage(preview.terminationPolicy().noticePayPercentage());
        settlement.setNoticePayAmount(preview.terminationPolicy().noticePayAmount());
        settlement.setForcedDeductionPercentage(preview.terminationPolicy().forcedDeductionPercentage());
        settlement.setForcedDeductionAmount(preview.terminationPolicy().forcedDeductionAmount());

        return settlement;
    }

    // ------------------------------------------------------
    // Persists exactly two leave rows: ANNUAL (kept separate — the
    // only encashable type) and MEDICAL_CASUAL (Medical/M1 + Casual
    // combined). Either may be absent if attendance had no current
    // cycle balance for that bucket.
    private void addLeave(
            SettlementPreviewDto preview,
            EmployeeFinalSettlement settlement) {

        if (preview.annualLeave() != null) {

            EmployeeFinalSettlementLeaveDetail entity = new EmployeeFinalSettlementLeaveDetail();

            entity.setLeaveTypeId(preview.annualLeave().leaveTypeId());
            entity.setLeaveCode(preview.annualLeave().leaveCode());
            entity.setLeaveName(preview.annualLeave().leaveName());

            entity.setCycleStart(preview.annualLeave().cycleStart());
            entity.setCycleEnd(preview.annualLeave().cycleEnd());

            entity.setAllocated(preview.annualLeave().allocated());
            entity.setEarned(preview.annualLeave().earned());
            entity.setUsed(preview.annualLeave().used());
            entity.setPending(preview.annualLeave().pending());
            entity.setPayableDays(preview.annualLeave().payableDays());
            entity.setExcessiveDays(preview.annualLeave().recoverableDays());
            entity.setEncashmentAmount(preview.annualLeave().encashmentAmount());
            entity.setExcessiveAmount(preview.annualLeave().recoveryAmount());

            entity.setCreatedAt(LocalDateTime.now());

            settlement.addLeaveDetail(entity);
        }

        if (preview.medicalAndCasualLeave() != null) {

            EmployeeFinalSettlementLeaveDetail entity = new EmployeeFinalSettlementLeaveDetail();

            entity.setLeaveTypeId(null);
            entity.setLeaveCode("MEDICAL_CASUAL");
            entity.setLeaveName("Medical + Casual (Combined)");

            entity.setAllocated(preview.medicalAndCasualLeave().allocated());
            entity.setEarned(preview.medicalAndCasualLeave().earned());
            entity.setUsed(preview.medicalAndCasualLeave().used());
            entity.setPending(preview.medicalAndCasualLeave().pending());
            entity.setPayableDays(preview.medicalAndCasualLeave().payableDays());
            entity.setExcessiveDays(preview.medicalAndCasualLeave().recoverableDays());
            entity.setEncashmentAmount(preview.medicalAndCasualLeave().encashmentAmount());
            entity.setExcessiveAmount(preview.medicalAndCasualLeave().recoveryAmount());

            entity.setCreatedAt(LocalDateTime.now());

            settlement.addLeaveDetail(entity);
        }
    }

    // ------------------------------------------------------
    private void addBonuses(
            SettlementPreviewDto preview,
            EmployeeFinalSettlement settlement) {

        if (preview.bonus() == null ||
                preview.bonus().events() == null ||
                preview.bonus().events().isEmpty()) {
            return;
        }

        for (SettlementPreviewDto.OutstandingBonus b : preview.bonus().events()) {

            EmployeeFinalSettlementBonusDetail entity = new EmployeeFinalSettlementBonusDetail();

            entity.setBonusEventId(b.bonusEventId());
            entity.setEventName(b.eventName());
            entity.setEventType(b.eventType());
            entity.setReferenceDate(b.referenceDate());
            entity.setEligibleAmount(b.eligibleAmount());
            entity.setPaidAmount(b.paidAmount());
            entity.setOutstandingAmount(b.outstandingAmount());
            entity.setCreatedAt(LocalDateTime.now());

            settlement.addBonus(entity);
        }
    }

    private void addPermanentAdvances(
            SettlementPreviewDto preview,
            EmployeeFinalSettlement settlement) {

        if (preview.permanentAdvances() == null
                || preview.permanentAdvances().advances() == null
                || preview.permanentAdvances().advances().isEmpty()) {

            return;
        }

        for (SettlementPreviewDto.PermanentAdvance advance
                : preview.permanentAdvances().advances()) {

            EmployeeFinalSettlementAdvanceDetail entity = new EmployeeFinalSettlementAdvanceDetail();

            entity.setAdvanceId(advance.advanceId());

            entity.setRequestCode(advance.requestCode());

            entity.setReceivingDate(advance.receivingDate());

            entity.setAdvanceStatus(AdvanceStatus.APPROVED);

            entity.setDescription(null);

            entity.setOriginalAmount(advance.originalAmount());

            entity.setRecoveredAmount(advance.recoveredAmount());

            entity.setOutstandingAmount(advance.outstandingAmount());

            entity.setMonthlyDeduction(advance.monthlyDeduction());

            entity.setBonusOffset(advance.bonusOffset());

            entity.setAnnualLeaveOffset(advance.annualLeaveOffset());

            /*
             * Nothing has been recovered by Final Settlement yet.
             */

            entity.setBonusRecovered(BigDecimal.ZERO);

            entity.setLeaveRecovered(BigDecimal.ZERO);

            entity.setSettlementRecovered(BigDecimal.ZERO);

            entity.setRemarks(null);

            entity.setCreatedAt(LocalDateTime.now());

            settlement.addAdvanceDetail(entity);
        }
    }

    // ------------------------------------------------------
    private void addDeductions(
            SettlementPreviewDto preview,
            EmployeeFinalSettlement settlement) {

        if (preview.outstandingDeductions() == null ||
                preview.outstandingDeductions().isEmpty()) {
            return;
        }

        for (SettlementPreviewDto.OutstandingDeduction d : preview.outstandingDeductions()) {

            EmployeeFinalSettlementDeduction entity = new EmployeeFinalSettlementDeduction();

            entity.setDeductionCode(d.code());
            entity.setAmount(d.amount());
            entity.setCreatedAt(LocalDateTime.now());

            settlement.addDeduction(entity);
        }
    }

    /*
     * ------------------------------------------------------------------------
     * Historical snapshots — Attendance / Normal Overtime
     * ------------------------------------------------------------------------
     * These reuse the exact attendanceSummary/normalOvertime already computed
     * by the preview (same SettlementPreviewDto) — no recalculation happens
     * here. Only invoked from settlement creation, never from preview, so
     * these tables only ever receive rows when HR actually creates the
     * settlement.
     */
    private void addAttendanceSnapshot(
            SettlementPreviewDto preview,
            EmployeeFinalSettlement settlement) {

        if (preview.attendanceSummary() == null
                || preview.attendanceSummary().attendance() == null) {
            return;
        }

        SettlementPreviewDto.AttendanceSummarySnapshot summary = preview.attendanceSummary();
        AttendanceSummaryDto attendance = summary.attendance();

        EmployeeFinalSettlementAttendance entity = EmployeeFinalSettlementAttendance.builder()
                .employeeId(summary.employeeId())
                .periodFrom(summary.from())
                .periodTo(summary.to())
                .presentDays(attendance.presentDays())
                .halfDays(attendance.halfDays())
                .leaveDays(attendance.leaveDays())
                .paidLeaveDays(attendance.paidLeaveDays())
                .unpaidLeaveDays(attendance.unpaidLeaveDays())
                .absentDays(attendance.absentDays())
                .weekendDays(attendance.weekendDays())
                .holidayDays(attendance.holidayDays())
                .build();

        settlement.addAttendanceDetail(entity);
    }

    private void addOvertimeSnapshot(
            SettlementPreviewDto preview,
            EmployeeFinalSettlement settlement) {

        if (preview.attendanceSummary() == null
                || preview.attendanceSummary().normalOvertime() == null) {
            return;
        }

        SettlementPreviewDto.NormalOvertimeSummary overtime =
                preview.attendanceSummary().normalOvertime();

        BigDecimal monthlyBasicSalary = preview.earnedSalary() != null
                ? preview.earnedSalary().monthlyBasicSalary()
                : BigDecimal.ZERO;

        // Audit-only inputs behind the already-computed amounts above — not a
        // second calculation of the amounts themselves.
        BigDecimal perDaySalary = Money.of(monthlyBasicSalary).divide(NORMAL_OT_DAYS_PER_MONTH).amount();
        BigDecimal perHourSalary = Money.of(perDaySalary).divide(NORMAL_OT_HOURS_PER_DAY).amount();

        EmployeeFinalSettlementOvertime entity = EmployeeFinalSettlementOvertime.builder()
                .employeeId(preview.employeeId())
                .overtimeType(SettlementOvertimeType.NORMAL)
                .approvedMinutes(overtime.approved().minutes())
                .approvedAmount(overtime.approved().amount())
                .pendingMinutes(overtime.pending().minutes())
                .pendingAmount(overtime.pending().amount())
                .unappliedMinutes(overtime.unapplied().minutes())
                .unappliedAmount(overtime.unapplied().amount())
                .totalMinutes(overtime.total().minutes())
                .totalAmount(overtime.total().amount())
                .monthlyBasicSalary(monthlyBasicSalary)
                .perDaySalary(perDaySalary)
                .perHourSalary(perHourSalary)
                .overtimeMultiplier(NORMAL_OT_MULTIPLIER)
                .build();

        settlement.addOvertimeDetail(entity);
    }

    public FinalSettlementDto get(Long settlementId) {

        EmployeeFinalSettlement settlement = settlementRepository.findById(settlementId)
                .orElseThrow(() -> settlementNotFound(settlementId));

        return FinalSettlementDto.from(settlement);
    }


    @Transactional
    public FinalSettlementDto hrApproval(
            Long settlementId,
            ApprovalRequest request) {

        EmployeeFinalSettlement settlement = settlementRepository.findById(settlementId)
                .orElseThrow(() -> settlementNotFound(settlementId));

        CurrentUser user = currentUser();

        workflowValidator.canApprove(settlement);

        requireBelongingsCleared(settlement);

        // get current DateTime
        LocalDateTime currentDateTime = LocalDateTime.now();

        settlement.setStatus(EmployeeFinalSettlementStatus.HR_APPROVED);

        settlement.setApprovedAt(currentDateTime);

        settlement.setRemarks(request.note());

        settlement.setApprovedBy(user.userId());

        settlement.setUpdatedAt(currentDateTime);

        settlementRepository.save(settlement);

        audit.emit(
                "employee_final_settlement",
                settlement.getId().toString(),
                "hr_approved",
                null,
                Map.of(
                        "employeeId", settlement.getEmployeeId(),
                        "status", settlement.getStatus(),
                        "approvedBy", settlement.getApprovedBy(),
                        "netSettlement", settlement.getNetSettlement()
                ),
                true,
                "Final settlement approved by HR"
        );

        return FinalSettlementDto.from(settlement);
    }


    @Transactional
    public FinalSettlementDto reject(Long settlementId, ApprovalRequest request) {
        EmployeeFinalSettlement settlement = settlementRepository.findById(settlementId)
                .orElseThrow(() -> settlementNotFound(settlementId));

        CurrentUser user = currentUser();

        workflowValidator.canReject(settlement);

        /*
         * --------------------------------------------------------
         * Undo the closing done at settlement CREATION
         * --------------------------------------------------------
         *
         * Leave encashment, bonus settlement, permanent/short-term
         * advance recovery, and their settlement-detail snapshots were
         * all finalized in create() (see paymentService.process()).
         * Rejecting here must reverse exactly that work, or the
         * employee stays "closed" against a settlement that no longer
         * exists.
         */
        paymentService.reverse(settlement);

        // get current DateTime
        LocalDateTime currentDateTime = LocalDateTime.now();

        settlement.setStatus(EmployeeFinalSettlementStatus.REJECTED);

        settlement.setRemarks(request.note());

        settlement.setApprovedAt(currentDateTime);

        settlement.setApprovedBy(user.userId());

        settlement.setUpdatedAt(currentDateTime);

        settlementRepository.save(settlement);

        audit.emit(
                "employee_final_settlement",
                settlement.getId().toString(),
                "rejected",
                null,
                Map.of(
                        "employeeId", settlement.getEmployeeId(),
                        "net", settlement.getNetSettlement(),
                        "status", settlement.getStatus(),
                        "Rejected By", settlement.getApprovedBy()

                ),
                true,
                "Final settlement rejected — leave/bonus/advances closing reversed"
        );

        /*
         * --------------------------------------------------------
         * Tell employee-service to undo the termination
         * --------------------------------------------------------
         *
         * The employee was terminated (status flipped, contracts
         * closed, ESS/auth disabled) as soon as the settlement was
         * created. Employee-service listens for this event and runs
         * the reverse of TerminationSaga.completeTermination.
         */
        outbox.publish(
                PayrollEvents.TOPIC,
                "final_settlement:" + settlement.getId(),
                PayrollEvents.FINAL_SETTLEMENT_REJECTED,
                new PayrollEvents.FinalSettlementRejected(
                        settlement.getId(),
                        settlement.getEmployeeId(),
                        settlement.getEmployeeCode(),
                        settlement.getEmployeeName(),
                        settlement.getLastWorkingDay(),
                        request.note(),
                        currentDateTime,
                        user.userId()
                )
        );

        return FinalSettlementDto.from(settlement);
    }


    @Transactional
    public FinalSettlementDto markPaid(Long settlementId, SettlementPaymentRequest request) {

        EmployeeFinalSettlement settlement = settlementRepository.findById(settlementId)
                .orElseThrow(() -> settlementNotFound(settlementId));

        workflowValidator.canMarkPaid(settlement);

        CurrentUser user = currentUser();

        // get current DateTime
        LocalDateTime currentDateTime = LocalDateTime.now();

        /*
         * --------------------------------------------------------
         * HR policy: closing (leave/bonus/advances/deductions) already
         * happened at settlement creation. This step is purely the
         * financial payment confirmation from Accounts — it records
         * paymentDate/paymentMethod/referenceNo and flips status to
         * PAY. It does not re-run FinalSettlementPaymentService.
         * --------------------------------------------------------
         */
        settlement.setStatus(EmployeeFinalSettlementStatus.PAID);

        settlement.setRemarks(request.remarks());

        settlement.setPaidAt(currentDateTime);
        settlement.setPaidBy(user.userId());

        settlement.setUpdatedAt(currentDateTime);

        audit.emit(
                "employee_final_settlement",
                settlement.getId().toString(),
                "paid",
                null,
                Map.of(
                        "employeeId", settlement.getEmployeeId(),
                        "netSettlement", settlement.getNetSettlement(),
                        "paymentMethod", request.paymentMethod(),
                        "referenceNo", request.referenceNo() == null ? "" : request.referenceNo(),
                        "paidBy", settlement.getPaidBy(),
                        "paid at", settlement.getPaidAt()
                ),
                true,
                "Final settlement payment confirmed"
        );

        outbox.publish(
                PayrollEvents.TOPIC,
                "final_settlement:" + settlement.getId(),
                PayrollEvents.FINAL_SETTLEMENT_PAID,
                new PayrollEvents.FinalSettlementPaid(
                        settlement.getId(), // settlement id means primary key of record in this table.
                        settlement.getEmployeeId(),
                        settlement.getEmployeeCode(),
                        settlement.getEmployeeName(),
                        settlement.getLastWorkingDay(),
                        settlement.getNetSettlement(),
                        request.paymentDate(),
                        settlement.getPaidAt(),
                        settlement.getPaidBy()
                )
        );

        return FinalSettlementDto.from(settlement);
    }

    @Transactional
    public FinalSettlementDto cancel(Long settlementId, CancelSettlementRequest request) {

        EmployeeFinalSettlement settlement = settlementRepository.findById(settlementId)
                .orElseThrow(() -> settlementNotFound(settlementId));

        CurrentUser user = currentUser();

        workflowValidator.canCancel(settlement);

        /*
         * --------------------------------------------------------
         * Undo the closing done at settlement CREATION
         * --------------------------------------------------------
         *
         * CANCEL is allowed from DRAFT or HR_APPROVED — both of which
         * already ran paymentService.process() at creation (leave
         * encashment, bonus settlement, permanent/short-term advance
         * recovery). Cancelling must reverse exactly that work, the
         * same as reject() does for DRAFT — otherwise the employee
         * stays "closed" (advances recovered, leave encashed, bonus
         * paid, and terminated) against a settlement that no longer
         * exists. Reject and cancel differ only in which stage
         * triggers them and in audit wording, never in side effect
         * completeness.
         */
        paymentService.reverse(settlement);

        LocalDateTime currentDateTime = LocalDateTime.now();

        settlement.setStatus(EmployeeFinalSettlementStatus.CANCELLED);
        settlement.setRemarks(request.reason());

        settlement.setApprovedAt(currentDateTime);
        settlement.setApprovedBy(user.userId());

        settlement.setUpdatedAt(currentDateTime);

        audit.emit(
                "employee_final_settlement",
                settlement.getId().toString(),
                "cancelled",
                null,
                Map.of(
                        "employeeId", settlement.getEmployeeId(),
                        "net", settlement.getNetSettlement(),
                        "Cancelled By", settlement.getApprovedBy(),
                        "Cancelled At", settlement.getApprovedAt()
                ),
                true,
                "Final settlement cancelled — leave/bonus/advances closing reversed"
        );

        /*
         * --------------------------------------------------------
         * Tell employee-service to undo the termination
         * --------------------------------------------------------
         *
         * Same as reject(): the employee was terminated (status
         * flipped, contracts closed, ESS/auth disabled) as soon as the
         * settlement was created. Employee-service listens for this
         * event and runs the reverse of TerminationSaga.completeTermination.
         */
        outbox.publish(
                PayrollEvents.TOPIC,
                "final_settlement:" + settlement.getId(),
                PayrollEvents.FINAL_SETTLEMENT_CANCELLED,
                new PayrollEvents.FinalSettlementCancelled(
                        settlement.getId(),
                        settlement.getEmployeeId(),
                        settlement.getEmployeeCode(),
                        settlement.getEmployeeName(),
                        settlement.getLastWorkingDay(),
                        request.reason(),
                        currentDateTime,
                        user.userId()
                )
        );

        return FinalSettlementDto.from(settlement);
    }

    /**
     * HR cannot approve a final settlement while the employee still holds
     * company belongings.
     *
     * <p>Checked here rather than at creation on purpose. Creation already
     * terminates the employee and closes the money; a settlement that sits
     * in DRAFT until the laptop and the motorcycle come back is exactly the
     * lever HR wants — and {@code reject} / {@code cancel} stay available to
     * back the whole thing out. Gating creation instead would stop HR even
     * recording the termination.
     *
     * <p>Read live from employee-service on every attempt, never snapshot
     * at creation: HR clears an asset and retries the approval seconds
     * later, so the only useful answer is the current one. The rule itself
     * (which activation / clearance statuses count) belongs to
     * employee-service — this method renders its verdict and refuses, it
     * does not re-derive it. If the check cannot run,
     * {@code EmployeeClient.belongingClearance} throws 502 rather than
     * letting the approval through.
     *
     * <p>The 409 carries the blocking list in the ProblemDetail extensions,
     * so a single response gives the FE both the message and the table to
     * render — no second round trip to build the dialog.
     */
    private void requireBelongingsCleared(EmployeeFinalSettlement settlement) {

        BelongingClearanceCheckDto clearance =
                employeeClient.belongingClearance(settlement.getEmployeeId());

        if (clearance.cleared()) {
            return;
        }

        throw new ConflictError(
                PayrollErrorCode.BELONGING_CLEARANCE_PENDING,
                ("%s still holds %d company belonging(s) that have not been cleared. "
                        + "Clear each one (return it, or record an APPROVED clearance) "
                        + "before approving this final settlement.")
                        .formatted(
                                settlement.getEmployeeName() == null
                                        ? "Employee " + settlement.getEmployeeId()
                                        : settlement.getEmployeeName(),
                                clearance.pendingCount()),
                // Map.of rejects nulls, and a 500 NPE here would hide the
                // real 409. Both upstream fields are optional on the wire.
                Map.of(
                        "employeeId", settlement.getEmployeeId(),
                        "pendingCount", clearance.pendingCount(),
                        "pendingAssetValue", clearance.pendingAssetValue() == null
                                ? BigDecimal.ZERO
                                : clearance.pendingAssetValue(),
                        "belongings", clearance.pending() == null
                                ? List.of()
                                : clearance.pending()
                )
        );
    }

    /**
     * 404 + {@code FINAL_SETTLEMENT_NOT_FOUND}, not the bare
     * {@code IllegalArgumentException} this used to throw — commons-web's
     * {@code GlobalExceptionHandler} has no handler for that, so it fell
     * through to {@code handleAny} and every missing settlement surfaced to
     * the FE as a 500 INTERNAL_SERVER_ERROR.
     */
    private NotFoundError settlementNotFound(Long settlementId) {
        return new NotFoundError(
                PayrollErrorCode.FINAL_SETTLEMENT_NOT_FOUND,
                "Settlement not found : " + settlementId);
    }

    private CurrentUser currentUser() {
        return CurrentUserHolder.get()
                .orElseThrow(() -> new UnauthorizedError("no current user"));
    }
}
