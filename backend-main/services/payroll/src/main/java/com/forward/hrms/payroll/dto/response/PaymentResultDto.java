package com.forward.hrms.payroll.dto.response;

import lombok.Builder;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;

@Builder
@Getter
@Setter
public class PaymentResultDto {

    private BigDecimal leavePaid;

    private BigDecimal bonusPaid;

    private BigDecimal advanceRecovered;

    private BigDecimal fairPriceRecovered;

    private BigDecimal deductionsRecovered;

    private BigDecimal netPaid;
}
