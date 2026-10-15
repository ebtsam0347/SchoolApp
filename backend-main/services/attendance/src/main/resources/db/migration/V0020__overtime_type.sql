-- ---------------------------------------------------------------------
-- Overtime type is now chosen by the applicant (NORMAL / EXTRA / SPECIAL)
-- instead of being auto-derived from the day. The chosen type selects the
-- single attendance_records OT bucket the day's overtime lands in.
-- ---------------------------------------------------------------------

alter table overtime_requests add column overtime_type varchar(16);

-- Backfill historical rows from the buckets recompute already wrote, so the
-- column reflects how each past request was actually classified.
update overtime_requests o
set overtime_type = case
    when ar.ot_special_minutes > 0 then 'SPECIAL'
    when ar.ot_extra_minutes   > 0 then 'EXTRA'
    else 'NORMAL'
end
from attendance_records ar
where ar.employee_id = o.employee_id
  and ar.attendance_date = o.attendance_date;

-- Rows with no matching attendance_record (or never approved) default to NORMAL.
update overtime_requests set overtime_type = 'NORMAL' where overtime_type is null;

alter table overtime_requests alter column overtime_type set not null;
