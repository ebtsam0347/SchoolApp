-- §7.payroll #25 — `sundays_in_period` was a misnamed counter for any
-- WEEKEND-status day in the daily attendance projection. Forward
-- Sports runs Friday-off shifts at some sites, so the column name was
-- misleading. Rename to `weekends_in_period`; data is unchanged.
alter table payslips rename column sundays_in_period to weekends_in_period;
