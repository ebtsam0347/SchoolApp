package com.forward.hrms.payroll.enums;

import java.math.BigDecimal;

public enum ForcedTerminationDeductionPercentage {

    PERCENT_0(BigDecimal.valueOf(0)),
    PERCENT_25(BigDecimal.valueOf(25)),
    PERCENT_50(BigDecimal.valueOf(50)),
    PERCENT_75(BigDecimal.valueOf(75)),
    PERCENT_100(BigDecimal.valueOf(100));

    private final BigDecimal percentage;

    ForcedTerminationDeductionPercentage(BigDecimal percentage) {
        this.percentage = percentage;
    }

    public BigDecimal percentage() {
        return percentage;
    }
}