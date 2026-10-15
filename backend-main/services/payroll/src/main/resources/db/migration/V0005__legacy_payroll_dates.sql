-- Adds two per-employee dates surfaced by the legacy WebHRMS migration:
--   - gratuity_start_date: when gratuity accrual began. Usually equals
--     joining_date but can differ (recognized prior service, contract
--     conversion). Locked once set; payroll runs read this directly.
--   - senior_allowance_start_date: when senior-allowance eligibility
--     began. NULL = ineligible. Non-NULL = the date used by payroll for
--     the seniority-tied allowance.
--
-- Both belong on the per-employee payroll settings row — they are
-- compensation inputs, not employee-profile facts.

alter table employee_payroll_settings
    add column gratuity_start_date         date,
    add column senior_allowance_start_date date;
