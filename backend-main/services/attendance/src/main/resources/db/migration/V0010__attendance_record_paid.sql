-- §7.payroll #23 — surface a `paid` flag on the daily attendance
-- projection so payroll can distinguish paid LEAVE (counts toward
-- paid days) from unpaid LEAVE (counts toward LOP).
--
-- Nullable column: only LEAVE rows carry a real value. PRESENT /
-- HALF_DAY / ABSENT / HOLIDAY / WEEKEND ignore it.
alter table attendance_records
    add column paid boolean null;

-- Backfill: every existing LEAVE row was approved against a leave
-- type whose paid flag is in leave_types. Look it up via the most
-- recent APPROVED leave day for that employee/date pair. Ambiguous
-- rows (rare — overlapping requests aren't allowed in practice but
-- the data didn't enforce it) default to true (paid) — matches
-- HR's read of "approved leave is paid unless the type says
-- otherwise."
update attendance_records ar
   set paid = lt.paid
  from leave_request_days lrd
  join leave_requests lr on lr.id = lrd.leave_request_id
  join leave_types lt on lt.id = lr.leave_type_id
 where ar.status = 'LEAVE'
   and ar.employee_id = lr.employee_id
   and ar.attendance_date = lrd.attendance_date
   and lrd.status = 'APPROVED';

-- Anything still null after the backfill (legacy LEAVE row with no
-- matching approved day) — default to paid.
update attendance_records
   set paid = true
 where status = 'LEAVE' and paid is null;
