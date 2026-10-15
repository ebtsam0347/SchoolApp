-- Leave entitlement cycle model. Replaces the dead accrual_rule enum
-- (ANNUAL/MONTHLY/NONE — stored but never read) with a real cycle anchor
-- that drives provisioning, the renewal job, proration, and encashment.
--
-- Policy seeded for the standard Forward Sports catalogue:
--   ANNUAL          → JOINING anniversary cycle, encashable, full from joining
--   M1, CASUAL      → FISCAL (Jul–Jun) cycle, prorated on mid-year entry, lapse
--   everything else → NONE (occasional / statutory; calendar-year, lazy)

-- ---------------------------------------------------------------------
-- leave_types: cycle anchor + proration + encashable flags.
-- ---------------------------------------------------------------------
alter table leave_types add column cycle_anchor     varchar(16);
alter table leave_types add column prorate_on_entry boolean not null default false;
alter table leave_types add column encashable       boolean not null default false;

update leave_types set cycle_anchor = 'JOINING', encashable = true
 where code = 'ANNUAL';
update leave_types set cycle_anchor = 'FISCAL', prorate_on_entry = true
 where code in ('M1', 'CASUAL');
update leave_types set cycle_anchor = 'NONE'
 where cycle_anchor is null;

alter table leave_types alter column cycle_anchor set not null;
alter table leave_types drop column accrual_rule;

-- ---------------------------------------------------------------------
-- leave_balances: explicit cycle window replaces the bare `year` key.
-- Existing rows were calendar-year buckets (provisioned via
-- LocalDate.now().getYear()); backfill them as Jan 1–Dec 31 of `year` to
-- preserve current lookup semantics. Go-forward cycles created by the new
-- code carry the correct JOINING / FISCAL / calendar windows. `year`
-- stays as a denormalized grouping label (= cycle_start year).
-- ---------------------------------------------------------------------
alter table leave_balances add column cycle_start   date;
alter table leave_balances add column cycle_end     date;
alter table leave_balances add column encashed_days numeric(5,2);

update leave_balances
   set cycle_start = make_date(year, 1, 1),
       cycle_end   = make_date(year, 12, 31)
 where cycle_start is null;

alter table leave_balances alter column cycle_start set not null;
alter table leave_balances alter column cycle_end   set not null;

alter table leave_balances drop constraint if exists leave_balances_employee_id_leave_type_id_year_key;
alter table leave_balances add constraint leave_balances_emp_type_cycle_key
    unique (employee_id, leave_type_id, cycle_start);

-- ---------------------------------------------------------------------
-- attendance_employees: mirror the joining date for cycle anchoring.
-- Populated from employee.created going forward; null for rows mirrored
-- before this column (backfilled lazily on the employee's next leave).
-- ---------------------------------------------------------------------
alter table attendance_employees add column joining_date date;
