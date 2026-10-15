-- Denormalize period info from payroll_runs onto payslips so listing
-- endpoints don't need a join (and DTOs can carry the period without a
-- run lookup).

alter table payslips
    add column period_year       int,
    add column period_month      int,
    add column fiscal_year_code  varchar(7);

update payslips p
   set period_year      = r.period_year,
       period_month     = r.period_month,
       fiscal_year_code = r.fiscal_year_code
  from payroll_runs r
 where p.payroll_run_id = r.id;

alter table payslips
    alter column period_year set not null,
    alter column period_month set not null,
    alter column fiscal_year_code set not null;

alter table payslips
    add constraint chk_payslip_month check (period_month between 1 and 12);

create index idx_payslips_employee_period
    on payslips (employee_id, period_year desc, period_month desc);
