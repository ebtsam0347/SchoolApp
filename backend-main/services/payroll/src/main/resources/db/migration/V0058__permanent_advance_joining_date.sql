-- Permanent advances — snapshot the employee's date of joining at request
-- time, read from employee-service when the advance is created (the same
-- call that checks the employee is ACTIVE). A snapshot, like
-- gratuity_at_request: not updated if the employee record changes later.
-- Nullable because advances created before this column have no value.
alter table permanent_advances add column joining_date date;
