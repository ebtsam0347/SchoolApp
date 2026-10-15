package com.forward.hrms.payroll.enums;

import java.math.BigDecimal;

public enum NoticePayPercentage {

    NONE(BigDecimal.ZERO),

    PERCENT_25(BigDecimal.valueOf(25)),

    PERCENT_50(BigDecimal.valueOf(50)),

    PERCENT_100(BigDecimal.valueOf(100));

    private final BigDecimal percentage;

    NoticePayPercentage(BigDecimal percentage) {
        this.percentage = percentage;
    }

    public BigDecimal percentage() {
        return percentage;
    }
}
