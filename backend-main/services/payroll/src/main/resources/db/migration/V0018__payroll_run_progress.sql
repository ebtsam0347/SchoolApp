-- Compute and post are now async via per-step sagas; the FE polls the run
-- row to know whether the spinner is still warranted. progress_count
-- ticks up every ~25 employees during compute (and every ~25 payslips
-- during post); total_count is the candidate count snapshot taken at
-- saga kickoff so the FE can render "2,341 / 5,012". Both reset to zero
-- on /reset; total_count is rewritten at each kickoff.
alter table payroll.payroll_runs
    add column progress_count int not null default 0,
    add column total_count    int not null default 0;
