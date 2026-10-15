package com.forward.hrms.payroll.events;

import com.forward.hrms.commons.kafka.event.KafkaTopics;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Map;

/**
 * Event-type strings + the payroll events topic, in one place. Keep the
 * names stable; downstream consumers (audit, BFF timeline) match on them.
 */
public final class AllowanceEvents {

    public static final String SERVICE_NAME = "allowance";
    public static final String TOPIC = KafkaTopics.events(SERVICE_NAME);


    public static final String RUN_CREATED                   = "allowance.run.created";
    public static final String RUN_COMPUTED                  = "allowance.run.computed";
    public static final String RUN_RESET                     = "allowance.run.reset";
    public static final String RUN_APPROVED                  = "allowance.run.approved";
    public static final String RUN_POSTED                    = "allowance.run.posted";
    public static final String ALLOWANCE_SLIP_GENERATED      = "allowance.slip.generated";
    public static final String ALLOWANCE_HELD                = "allowance.held";
    public static final String ALLOWANCE_UNHELD              = "allowance.unheld";
    public static final String ALLOWANCE_RELEASE_QUEUED      = "allowance.release_queued";
    public static final String ALLOWANCE_RELEASED            = "allowance.released";

    private AllowanceEvents() {}

    public record BasicRevised(
            long employeeId, BigDecimal newBasic, LocalDate effectiveFrom, String by) {}

    public record SalaryLineChanged(
            long employeeId, long componentId, String componentCode, String type,
            BigDecimal fromAmount, BigDecimal toAmount, LocalDate effectiveFrom, String action) {}

    public record EobiEnrollmentChanged(long employeeId, boolean eobiEnrolled, String by) {}

    public record PaymentModeChanged(long employeeId, String fromMode, String toMode, String by) {}

    public record BankDetailsChanged(long employeeId, String action, String by) {}

    public record IncrementBatchApplied(
            long batchId, String name, LocalDate effectiveFrom,
            int employeeCount, String by) {}

    public record OffCyclePaymentChanged(
            long paymentId, long employeeId, String action, LocalDate paidOn,
            BigDecimal amount, String category, String by) {}

    public record BonusEventActivated(
            long eventId, String name, String eventType, String fiscalYearCode,
            LocalDate referenceDate, int employeeCount, BigDecimal totalFullBonus, String by) {}

    public record BonusInstallmentApplied(
            long installmentId, long eventId, String eventName, int installmentNo,
            BigDecimal percentage, LocalDate paidOn,
            int employeeCount, BigDecimal totalAmount, String by) {}

    public record RunCreated(long runId, String fiscalYearCode, int year, int month) {}

    public record RunComputed(long runId, String fiscalYearCode, int employeeCount, BigDecimal totalNet) {}

    public record RunReset(long runId, String fiscalYearCode, int deletedPayslips, String by) {}

    public record RunApproved(long runId, String fiscalYearCode) {}

    public record RunPosted(long runId, String fiscalYearCode) {}

    public record PayslipGenerated(
            long payslipId, long employeeId, String fiscalYearCode,
            String period, BigDecimal net, String pdfKey) {}

    public record SalaryHeld(
            long payslipId, long employeeId, long runId, String reason, String by) {}

    public record SalaryUnheld(
            long payslipId, long employeeId, long runId, String reason, String by) {}

    public record SalaryReleaseQueued(
            long heldPayslipId, long employeeId, long runId,
            BigDecimal amount, String reason, String by) {}

    public record SalaryReleased(
            long heldPayslipId, long employeeId, long releasedInRunId, BigDecimal amount) {}

    /** Schemaless map carrier for ad-hoc payloads we don't pin. */
    public record GenericPayload(Map<String, Object> data) {}


}
