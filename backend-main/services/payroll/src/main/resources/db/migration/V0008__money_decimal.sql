-- ---------------------------------------------------------------------
-- §0.5.A — money columns: bigint paisa → numeric(15,2) rupees.
--
-- Every paisa column is divided by 100 to land at rupee scale. Single
-- transaction; payroll table sizes are small. CHECK constraints on
-- amount/basic columns (`>= 0`, `> 0`) survive the type change because
-- the underlying value preserves sign.
--
-- Out of scope here:
--   * pessi_rate_percent (statutory_config) — already numeric(5,2).
--   * lop_days, ot_hours, days_encashed, percentage — already numeric.
--   * id / employee_id / *_by columns — bigint identifiers, not money.
-- ---------------------------------------------------------------------

-- statutory_config
alter table statutory_config alter column eobi_amount       type numeric(15,2) using (eobi_amount       / 100.0::numeric);
alter table statutory_config alter column pessi_salary_cap  type numeric(15,2) using (pessi_salary_cap  / 100.0::numeric);

-- salary_components catalog
alter table salary_components alter column default_amount   type numeric(15,2) using (default_amount    / 100.0::numeric);

-- employee salary
alter table employee_salaries          alter column basic   type numeric(15,2) using (basic             / 100.0::numeric);
alter table employee_salary_components alter column amount  type numeric(15,2) using (amount            / 100.0::numeric);

-- payslips + payslip_lines
alter table payslips alter column basic           type numeric(15,2) using (basic           / 100.0::numeric);
alter table payslips alter column allowances      type numeric(15,2) using (allowances      / 100.0::numeric);
alter table payslips alter column gross           type numeric(15,2) using (gross           / 100.0::numeric);
alter table payslips alter column deductions      type numeric(15,2) using (deductions      / 100.0::numeric);
alter table payslips alter column tax             type numeric(15,2) using (tax             / 100.0::numeric);
alter table payslips alter column eobi            type numeric(15,2) using (eobi            / 100.0::numeric);
alter table payslips alter column pessi_employer  type numeric(15,2) using (pessi_employer  / 100.0::numeric);
alter table payslips alter column net             type numeric(15,2) using (net             / 100.0::numeric);
alter table payslips alter column ot_amount       type numeric(15,2) using (ot_amount       / 100.0::numeric);

alter table payslip_lines alter column rate       type numeric(15,2) using (rate            / 100.0::numeric);
alter table payslip_lines alter column amount     type numeric(15,2) using (amount          / 100.0::numeric);

-- bonuses
alter table bonus_event_amounts alter column basic_snapshot type numeric(15,2) using (basic_snapshot / 100.0::numeric);
alter table bonus_event_amounts alter column full_bonus     type numeric(15,2) using (full_bonus     / 100.0::numeric);

-- off-cycle payments
alter table off_cycle_payments alter column amount          type numeric(15,2) using (amount          / 100.0::numeric);

-- increment batches
alter table increment_batch_lines alter column old_basic       type numeric(15,2) using (old_basic       / 100.0::numeric);
alter table increment_batch_lines alter column new_basic       type numeric(15,2) using (new_basic       / 100.0::numeric);
alter table increment_batch_lines alter column old_income_tax  type numeric(15,2) using (old_income_tax  / 100.0::numeric);
alter table increment_batch_lines alter column new_income_tax  type numeric(15,2) using (new_income_tax  / 100.0::numeric);

-- permanent advances + per-period deductions
alter table permanent_advances alter column amount               type numeric(15,2) using (amount               / 100.0::numeric);
alter table permanent_advances alter column monthly_deduction    type numeric(15,2) using (monthly_deduction    / 100.0::numeric);
alter table permanent_advances alter column bonus_offset         type numeric(15,2) using (bonus_offset         / 100.0::numeric);
alter table permanent_advances alter column annual_leave_offset  type numeric(15,2) using (annual_leave_offset  / 100.0::numeric);
alter table permanent_advances alter column gratuity_at_request  type numeric(15,2) using (gratuity_at_request  / 100.0::numeric);

alter table permanent_advance_deductions alter column deducted_amount        type numeric(15,2) using (deducted_amount        / 100.0::numeric);
alter table permanent_advance_deductions alter column bonus_deduction        type numeric(15,2) using (bonus_deduction        / 100.0::numeric);
alter table permanent_advance_deductions alter column annual_leave_deduction type numeric(15,2) using (annual_leave_deduction / 100.0::numeric);
alter table permanent_advance_deductions alter column remaining_balance      type numeric(15,2) using (remaining_balance      / 100.0::numeric);

-- short-term advance batches + lines
alter table short_term_advance_batches alter column total_amount type numeric(15,2) using (total_amount / 100.0::numeric);
alter table short_term_advances        alter column amount       type numeric(15,2) using (amount       / 100.0::numeric);

-- leave encashments
alter table leave_encashments alter column basic_salary type numeric(15,2) using (basic_salary / 100.0::numeric);
alter table leave_encashments alter column amount       type numeric(15,2) using (amount       / 100.0::numeric);
