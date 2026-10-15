-- §7.employee #70 — covering index for HeadcountTrendService.headcountAsOf,
-- which runs `not exists (... status = 'COMPLETED' and last_working_day <= :asOf)`
-- against employee_exits per asOf-date in the trend window. The previous
-- single-column idx_exits_status forces a recheck on the heap; the
-- composite (status, last_working_day, employee_id) lets the planner
-- satisfy the anti-join from the index alone.
create index if not exists idx_exits_status_lwd_employee
    on employee_exits (status, last_working_day, employee_id);
