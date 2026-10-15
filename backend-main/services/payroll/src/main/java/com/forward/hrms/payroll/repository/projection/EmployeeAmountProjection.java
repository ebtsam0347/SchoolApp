package com.forward.hrms.payroll.repository.projection;

import java.math.BigDecimal;

/** One employee's summed amount for an accounts report line. */
public interface EmployeeAmountProjection {

    Long getEmployeeId();

    BigDecimal getAmount();
}
