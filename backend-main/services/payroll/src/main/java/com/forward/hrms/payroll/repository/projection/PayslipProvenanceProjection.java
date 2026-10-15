package com.forward.hrms.payroll.repository.projection;

/**
 * Read-only (id, created_by) pair of a payslip. Used only to tell a bulk-imported payslip
 * ({@code legacy-migration}) from an engine-computed one when deciding whether advance amounts are
 * already inside the persisted deductions; nothing is written.
 */
public interface PayslipProvenanceProjection {

    Long getId();

    String getCreatedBy();
}
