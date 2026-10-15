-- Every employee is EOBI-enrolled by default; legacy migration and the
-- new-employee onboarding path (EmployeePayrollSettingsService.ensureExists)
-- both set the flag to true. Flip the column default so it matches that
-- intent — the default is only consulted when an INSERT omits the column,
-- which would otherwise quietly fall to false. Existing rows are
-- unaffected (defaults don't backfill).
alter table employee_payroll_settings alter column eobi_enrolled set default true;
