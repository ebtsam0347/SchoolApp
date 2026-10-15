-- Deductions belong to the employee, not to one advance: the balance is
-- (APPROVED advances − all deductions) per employee, and source /
-- source_ref_id identify where each row came from. Where a per-advance figure
-- is still shown, it is derived by applying the employee's deductions to
-- their advances oldest-first.

alter table permanent_advance_deductions drop column advance_id;

-- Every balance and listing now reads by employee.
create index idx_perm_adv_ded_employee
    on permanent_advance_deductions (employee_id, transaction_date desc);
