package com.forward.hrms.payroll.settlement.context;

import lombok.Builder;
import lombok.Value;

import java.math.BigDecimal;

@Value
@Builder
public class PermanentAdvanceSettlement {

    /**
     * Current outstanding balance after all previous recoveries.
     */
    @Builder.Default
    BigDecimal outstandingBalance = BigDecimal.ZERO;

    /**
     * Amount recoverable from outstanding bonus.
     */
    @Builder.Default
    BigDecimal recoverFromBonus = BigDecimal.ZERO;

    /**
     * Amount recoverable from annual leave encashment.
     */
    @Builder.Default
    BigDecimal recoverFromAnnualLeave = BigDecimal.ZERO;

    /**
     * Remaining amount recoverable from gratuity/final settlement.
     */
    @Builder.Default
    BigDecimal recoverFromSettlement = BigDecimal.ZERO;

    /**
     * Total recovered during settlement.
     */
    @Builder.Default
    BigDecimal totalRecovery = BigDecimal.ZERO;
}