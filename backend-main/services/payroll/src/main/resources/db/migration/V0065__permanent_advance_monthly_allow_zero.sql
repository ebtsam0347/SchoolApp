-- A permanent advance's installment may be 0: HR pauses recovery for a
-- skipped employee from the payroll-run drawer, and compute skips an
-- advance whose monthly_deduction is 0 until it is raised again.
alter table permanent_advances drop constraint chk_perm_adv_monthly;
alter table permanent_advances
    add constraint chk_perm_adv_monthly check (monthly_deduction >= 0);
