package com.forward.hrms.payroll.repository.projection;


import java.math.BigDecimal;
import java.time.LocalDate;

public interface PermanentAdvanceSettlementProjection {

    Long getAdvanceId();

    String getRequestCode();

    LocalDate getReceivingDate();

    BigDecimal getOriginalAmount();

    BigDecimal getMonthlyDeduction();

    BigDecimal getBonusOffset();

    BigDecimal getAnnualLeaveOffset();

    BigDecimal getRecoveredAmount();

    BigDecimal getOutstandingAmount();
}