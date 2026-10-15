package com.forward.hrms.payroll.settlement.context;

import com.forward.hrms.payroll.dto.response.SettlementPreviewDto;
import com.forward.hrms.payroll.dto.response.TerminationLeavesSummaryDto;
import com.forward.hrms.payroll.enums.ForcedTerminationDeductionPercentage;
import com.forward.hrms.payroll.enums.NoticePayPercentage;
import com.forward.hrms.payroll.enums.SettlementAdjustmentType;
import com.forward.hrms.payroll.repository.projection.PermanentAdvanceSettlementProjection;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

@NoArgsConstructor
@Getter
@Setter
@ToString
public class SettlementCalculationResult {

    private BigDecimal monthlyBasicSalary = BigDecimal.ZERO;  // actual basic salary of employee.

    private BigDecimal basicEarnedSalary = BigDecimal.ZERO; // terminating time monthly salary not actual basic.

    private BigDecimal allowances = BigDecimal.ZERO;

    private BigDecimal grossSalary = BigDecimal.ZERO;

    private BigDecimal gratuity = BigDecimal.ZERO;

    private BigDecimal permanentAdvanceTotal = BigDecimal.ZERO;

    private BigDecimal shortTermAdvance = BigDecimal.ZERO;

    /** Outstanding RFD advances recovered at settlement (Audit Finding 6). */
    private BigDecimal rfdAdvanceAmount = BigDecimal.ZERO;

    private BigDecimal outstandingBonus = BigDecimal.ZERO;

    /**
     * Working behind {@link #outstandingBonus} at termination — day
     * counts, basic, rate and the 90-day eligibility flag — so HR can
     * verify the figure in the preview.
     */
    private SettlementPreviewDto.TerminationBonus terminationBonus;

    // it was only handling/designed for single leave type
    //private TerminationLeaveSummaryDto leaveSummary;
    // now we need multiple leaves records format
    private TerminationLeavesSummaryDto leavesSummaries;

    private BigDecimal leaveEncashment = BigDecimal.ZERO;

    private BigDecimal leaveRecovery = BigDecimal.ZERO;

    /*
     * "Excessive leave" = days used beyond what was earned, valued in
     * money. Annual is tracked separately (it's also the only
     * encashable type); Medical(M1) + Casual are tracked as one
     * combined bucket. The two *Amount fields together always equal
     * leaveRecovery above (already flowing into totalDeductions) —
     * these just carry the split breakdown for the settlement header.
     */
    private BigDecimal annualExcessiveDays = BigDecimal.ZERO;

    private BigDecimal annualExcessiveAmount = BigDecimal.ZERO;

    private BigDecimal medicalCasualExcessiveDays = BigDecimal.ZERO;

    private BigDecimal medicalCasualExcessiveAmount = BigDecimal.ZERO;

    private BigDecimal fairPriceShopAmount = BigDecimal.ZERO;

    /**
     * Grade-conditional canteen deduction for the settlement period,
     * computed by {@code SettlementCanteenCalculator} the same way the
     * regular payroll run does — a terminating employee's canteen usage
     * for the period wasn't otherwise charged anywhere.
     */
    private BigDecimal canteenAmount = BigDecimal.ZERO;

    private SettlementAdjustmentType adjustmentType = SettlementAdjustmentType.NONE;

    private NoticePayPercentage noticePayPercentage = NoticePayPercentage.NONE;

    private BigDecimal noticePayAmount =  BigDecimal.ZERO;

    private ForcedTerminationDeductionPercentage forcedDeductionPercentage = ForcedTerminationDeductionPercentage.PERCENT_0;

    private BigDecimal forcedDeductionAmount = BigDecimal.ZERO;

    private BigDecimal incomeTax = BigDecimal.ZERO;

    private BigDecimal otherDeductions = BigDecimal.ZERO;

    private BigDecimal grossSettlement = BigDecimal.ZERO;

    private BigDecimal totalDeductions = BigDecimal.ZERO;

    private BigDecimal netSettlement = BigDecimal.ZERO;

    private boolean gratuityEligible;

    private int gratuityYears;

    private List<PermanentAdvanceSettlementProjection> permanentAdvances = new ArrayList<>();
    private final List<SettlementPreviewDto.OutstandingDeduction> deductions = new ArrayList<>();
    private final List<SettlementPreviewDto.OutstandingBonus> bonuses = new ArrayList<>();
}
