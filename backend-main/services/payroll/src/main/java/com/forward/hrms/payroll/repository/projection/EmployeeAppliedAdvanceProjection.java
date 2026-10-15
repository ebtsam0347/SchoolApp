package com.forward.hrms.payroll.repository.projection;

import java.math.BigDecimal;

/** One employee's summed advance amount (applied principal, or deducted in a run) for the slip's advances block. */
public interface EmployeeAppliedAdvanceProjection {

    Long getEmployeeId();

    BigDecimal getAppliedAmount();
}
