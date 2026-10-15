package com.forward.hrms.bff.dto.response.dashboard;

import io.swagger.v3.oas.annotations.media.Schema;

import java.math.BigDecimal;

@Schema(name = "DashboardMoney",
        description = "Money envelope used across the dashboard payloads. `amount` is rupees as a " +
                "BigDecimal at scale 2 — matches the §0.5 doctrine (was historically `long` paisa).")
public record Money(BigDecimal amount, String currency) {

    public static final String PKR = "PKR";

    public static Money pkr(BigDecimal amount) {
        return new Money(amount, PKR);
    }
}
