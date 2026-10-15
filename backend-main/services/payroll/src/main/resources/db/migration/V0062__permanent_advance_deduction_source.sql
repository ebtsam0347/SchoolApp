-- Every permanent-advance deduction row is now one recovery from one place,
-- amount always in deducted_amount:
--   source        SALARY | BONUS | ANNUAL_LEAVE | MANUAL | SETTLEMENT
--   source_ref_id payroll run id | bonus transaction id | annual leave
--                 transaction id | (none) | final settlement id
-- Undoing a source deletes its rows by (source, source_ref_id). This replaces
-- payroll_run_id, final_settlement_id, bonus_deduction and
-- annual_leave_deduction.

alter table permanent_advance_deductions
    add column source        varchar(16),
    add column source_ref_id bigint;

-- 1. Classify existing rows.
update permanent_advance_deductions
   set source = 'SETTLEMENT', source_ref_id = final_settlement_id
 where final_settlement_id is not null;

-- Payroll-run installments and legacy salary deductions (legacy rows were
-- matched to a run by period; payroll_run_id is null where none existed).
update permanent_advance_deductions
   set source = 'SALARY', source_ref_id = payroll_run_id
 where source is null
   and (created_by = 'legacy-migration' or description ilike 'Deduction from salary%');

update permanent_advance_deductions
   set source = 'MANUAL'
 where source is null;

-- 2. Legacy rows carried bonus / annual-leave recoveries in their own
--    columns, on top of deducted_amount. Give each its own row so no repaid
--    amount is lost when the columns go. Settlement rows are skipped: their
--    deducted_amount is already the full recovery and the two columns only
--    said where it came from (still on employee_final_settlement_advance_details).
insert into permanent_advance_deductions (
    advance_id, employee_id, deducted_amount, remaining_balance, transaction_date,
    description, source, source_ref_id,
    created_at, updated_at, created_by, updated_by, ip_address)
select advance_id, employee_id, bonus_deduction, remaining_balance, transaction_date,
       'Deduction from bonus', 'BONUS', null,
       created_at, updated_at, created_by, updated_by, ip_address
  from permanent_advance_deductions
 where source <> 'SETTLEMENT'
   and bonus_deduction > 0;

insert into permanent_advance_deductions (
    advance_id, employee_id, deducted_amount, remaining_balance, transaction_date,
    description, source, source_ref_id,
    created_at, updated_at, created_by, updated_by, ip_address)
select advance_id, employee_id, annual_leave_deduction, remaining_balance, transaction_date,
       'Deduction from Annual leave ' || to_char(transaction_date, 'FMMonth YYYY'), 'ANNUAL_LEAVE', null,
       created_at, updated_at, created_by, updated_by, ip_address
  from permanent_advance_deductions
 where source <> 'SETTLEMENT'
   and annual_leave_deduction > 0;

-- 3. Lock it down and drop the replaced columns (their index and FK go with them).
alter table permanent_advance_deductions alter column source set not null;
alter table permanent_advance_deductions add constraint chk_perm_adv_ded_source check (source in (
    'SALARY', 'BONUS', 'ANNUAL_LEAVE', 'MANUAL', 'SETTLEMENT'));

create index idx_perm_adv_ded_source on permanent_advance_deductions (source, source_ref_id);

alter table permanent_advance_deductions
    drop column payroll_run_id,
    drop column final_settlement_id,
    drop column bonus_deduction,
    drop column annual_leave_deduction;
