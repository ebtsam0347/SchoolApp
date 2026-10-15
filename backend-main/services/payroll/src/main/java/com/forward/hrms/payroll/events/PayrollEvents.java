package com.forward.hrms.payroll.events;

import com.forward.hrms.commons.kafka.event.KafkaTopics;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Map;

/**
 * Event-type strings + the payroll events topic, in one place. Keep the
 * names stable; downstream consumers (audit, BFF timeline) match on them.
 */
public final class PayrollEvents {

    public static final String SERVICE_NAME = "payroll";
    public static final String TOPIC = KafkaTopics.events(SERVICE_NAME);

    public static final String BASIC_REVISED = "payroll.basic_revised";
    public static final String SALARY_LINE_CHANGED = "payroll.salary_line_changed";
    public static final String EOBI_ENROLLMENT_CHANGED = "payroll.eobi_enrollment_changed";
    public static final String PAYMENT_MODE_CHANGED = "payroll.payment_mode_changed";
    public static final String BANK_DETAILS_CHANGED = "payroll.bank_details_changed";
    public static final String INCREMENT_BATCH_APPLIED = "payroll.increment_batch.applied";
    public static final String OFF_CYCLE_PAYMENT_CHANGED = "payroll.off_cycle_payment.changed";
    public static final String BONUS_EVENT_ACTIVATED = "payroll.bonus_event.activated";
    public static final String BONUS_INSTALLMENT_APPLIED = "payroll.bonus_installment.applied";
    public static final String RUN_CREATED = "payroll.run.created";
    public static final String RUN_COMPUTED = "payroll.run.computed";
    public static final String RUN_RESET = "payroll.run.reset";
    public static final String RUN_APPROVED = "payroll.run.approved";
    public static final String RUN_POSTED = "payroll.run.posted";
    public static final String PAYSLIP_GENERATED = "payroll.payslip.generated";
    public static final String SALARY_HELD = "payroll.salary_held";
    public static final String SALARY_UNHELD = "payroll.salary_unheld";
    public static final String SALARY_RELEASE_QUEUED = "payroll.salary_release_queued";
    public static final String SALARY_RELEASED = "payroll.salary_released";

    // new event in payroll as the employee gets the terminated & get & pay the payment
    public static final String FINAL_SETTLEMENT_PAID = "payroll.final_settlement.paid";
    // new event when the settlement create by HR for employee service to create exit record as it published
    public static final String FINAL_SETTLEMENT_CREATED = "payroll.final_settlement.created";
    // new event in to initialize the termination settlement.
    public static final String FINAL_SETTLEMENT_DRAFTED = "payroll.final_settlement.drafted";
    // new event so employee-service can undo termination when HR rejects a DRAFT settlement.
    public static final String FINAL_SETTLEMENT_REJECTED = "payroll.final_settlement.rejected";
    // HR canceled a DRAFT or HR_APPROVED settlement — employee-service
    // handles this identically to FINAL_SETTLEMENT_REJECTED (undo the
    // termination performed at settlement creation). Reject and cancel
    // differ only in which stage triggers them and in audit wording,
    // never in side effect completeness.
    public static final String FINAL_SETTLEMENT_CANCELLED = "payroll.final_settlement.cancelled";

    private PayrollEvents() {
    }

    public record BasicRevised(
            long employeeId, BigDecimal newBasic, LocalDate effectiveFrom, String by) {
    }

    public record SalaryLineChanged(
            long employeeId, long componentId, String componentCode, String type,
            BigDecimal fromAmount, BigDecimal toAmount, LocalDate effectiveFrom, String action) {
    }

    public record EobiEnrollmentChanged(long employeeId, boolean eobiEnrolled, String by) {
    }

    public record PaymentModeChanged(long employeeId, String fromMode, String toMode, String by) {
    }

    public record BankDetailsChanged(long employeeId, String action, String by) {
    }

    public record IncrementBatchApplied(
            long batchId, String name, LocalDate effectiveFrom,
            int employeeCount, String by) {
    }

    public record OffCyclePaymentChanged(
            long paymentId, long employeeId, String action, LocalDate paidOn,
            BigDecimal amount, String category, String status, String by) {
    }

    public record BonusEventActivated(
            long eventId, String name, String eventType, String fiscalYearCode,
            LocalDate referenceDate, int employeeCount, BigDecimal totalFullBonus, String by) {
    }

    public record BonusInstallmentApplied(
            long installmentId, long eventId, String eventName, int installmentNo,
            BigDecimal percentage, LocalDate paidOn,
            int employeeCount, BigDecimal totalAmount, String by) {
    }

    public record RunCreated(long runId, String fiscalYearCode, int year, int month) {
    }

    public record RunComputed(long runId, String fiscalYearCode, int employeeCount, BigDecimal totalNet) {
    }

    public record RunReset(long runId, String fiscalYearCode, int deletedPayslips, String by) {
    }

    public record RunApproved(long runId, String fiscalYearCode) {
    }

    public record RunPosted(long runId, String fiscalYearCode) {
    }

    public record PayslipGenerated(
            long payslipId, long employeeId, String fiscalYearCode,
            String period, BigDecimal net, String pdfKey) {
    }

    public record SalaryHeld(
            long payslipId, long employeeId, long runId, String reason, String by) {
    }

    public record SalaryUnheld(
            long payslipId, long employeeId, long runId, String reason, String by) {
    }

    public record SalaryReleaseQueued(
            long heldPayslipId, long employeeId, long runId,
            BigDecimal amount, String reason, String by) {
    }

    public record SalaryReleased(
            long heldPayslipId, long employeeId, long releasedInRunId, BigDecimal amount) {
    }

    /**
     * Schemaless map carrier for ad-hoc payloads we don't pin.
     */
    public record GenericPayload(Map<String, Object> data) {
    }

    // **************************************************************
    // as each event has payload, so the final settlement paid payload.
    // **************************************************************
    public record FinalSettlementPaid(
            long settlementId, long employeeId, String employeeCode, String employeeName,
            LocalDate lastWorkingDay, BigDecimal netSettlement, LocalDate paymentDate,
            LocalDateTime paidAt, long paidBy
    ) {
    }

    // payload of settlement creation event
    public record FinalSettlementCreated(
            long settlementId, long employeeId, String employeeCode,
            String employeeName, LocalDate lastWorkingDay, String terminationReason,
            BigDecimal netSettlement,
            LocalDate createdDate, long createdBy
    ) {
    }

    // payload of settlement drafted event
    public record FinalSettlementDrafted(
            long settlementId, long employeeId, String employeeCode,
            String employeeName, LocalDate lastWorkingDay, String terminationReason,
            BigDecimal netSettlement,
            LocalDate createdDate, long createdBy
    ) {
    }

    /**
     * Payload of the settlement rejection event. Employee-service uses
     * this to undo the termination it applied when the settlement was
     * created (restore employment status, contracts, history, re-enable
     * ESS/auth access).
     */
    public record FinalSettlementRejected(
            long settlementId, long employeeId, String employeeCode,
            String employeeName, LocalDate lastWorkingDay, String reason,
            LocalDateTime rejectedAt, long rejectedBy
    ) {
    }

    /**
     * Payload of the settlement cancellation event — same shape as
     * {@link FinalSettlementRejected}.
     */
    public record FinalSettlementCancelled(
            long settlementId, long employeeId, String employeeCode,
            String employeeName, LocalDate lastWorkingDay, String reason,
            LocalDateTime cancelledAt, long cancelledBy
    ) {
    }
}
