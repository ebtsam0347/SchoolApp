package com.forward.hrms.payroll.repository.projection;

import java.math.BigDecimal;

/** One employee's summed amount for one salary component code. */
public interface EmployeeCodeAmountProjection {

    Long getEmployeeId();

    String getCode();

    BigDecimal getAmount();
}
