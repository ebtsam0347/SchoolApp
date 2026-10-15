package com.forward.hrms.employee.events.payroll;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

public final class PayrollEvents {

    public static final String TOPIC = "payroll.events";

    /**
     * Employee service does not need all Payroll events.
     * Only the events it consumes for now just final_settlement.paid event which will payroll publish/send to employee service.
     * this event will Publish after Accounts successfully pays employee final settlement.
     */
    public static final String FINAL_SETTLEMENT_PAID = "payroll.final_settlement.paid";

    /**
     * another event I need for employee settlements relevant now it is created settlement event
     */
    public static final String FINAL_SETTLEMENT_CREATED = "payroll.final_settlement.created";

    /**
     * HR rejected a DRAFT final settlement. Payroll has already reversed
     * its own financial closing (leave encashment, bonus, advances) —
     * employee-service must undo the termination it performed at
     * settlement creation (status, contracts, history, suspensions, ESS).
     */
    public static final String FINAL_SETTLEMENT_REJECTED = "payroll.final_settlement.rejected";

    /**
     * HR canceled a DRAFT or HR_APPROVED final settlement. Same
     * employee-service handling as {@link #FINAL_SETTLEMENT_REJECTED} —
     * payroll has reversed its own financial closing and this service
     * must undo the termination performed at settlement creation.
     * Reject and cancel differ only in which settlement stage triggers
     * them and in audit/workflow wording, never in side effect
     * completeness.
     */
    public static final String FINAL_SETTLEMENT_CANCELLED = "payroll.final_settlement.cancelled";


    private PayrollEvents() {
    }

    /**
     * Payload of paid payment event
     */
    public record FinalSettlementPaid(

            long settlementId,

            long employeeId,

            String employeeCode,

            String employeeName,

            LocalDate lastWorkingDay,

            BigDecimal netSettlement,

            LocalDate paymentDate,

            LocalDateTime paidAt,

            long paidBy

    ) {
    }


    /**
     * Payload of as the settlement creation event
     */
    public record FinalSettlementCreated(

            long settlementId,

            long employeeId,

            String employeeCode,

            String employeeName,

            LocalDate lastWorkingDay,

            String terminationReason,

            BigDecimal netSettlement,

            //EmployeeFinalSettlementStatus status,

            LocalDate createdDate,

            long createdBy

    ) {
    }

    /**
     * Payload of the settlement rejection event.
     */
    public record FinalSettlementRejected(

            long settlementId,

            long employeeId,

            String employeeCode,

            String employeeName,

            LocalDate lastWorkingDay,

            String reason,

            LocalDateTime rejectedAt,

            long rejectedBy

    ) {
    }

    /**
     * Payload of the settlement cancellation event — same shape as
     * {@link FinalSettlementRejected}.
     */
    public record FinalSettlementCancelled(

            long settlementId,

            long employeeId,

            String employeeCode,

            String employeeName,

            LocalDate lastWorkingDay,

            String reason,

            LocalDateTime cancelledAt,

            long cancelledBy

    ) {
    }

}