-- APPROVED is the live state for both advance kinds: the payroll run deducts
-- against it directly, so there is no disbursement step and no separate
-- DEDUCTED / CLOSED marker. Final settlement still stamps closed_at (permanent)
-- and final_settlement_id (short-term) for its own bookkeeping.
--
-- Fold existing rows into APPROVED before tightening the checks, otherwise
-- the new constraints fail on live data.

update short_term_advances
   set status = 'APPROVED'
 where status = 'DEDUCTED';

update short_term_advance_batches
   set status = 'APPROVED'
 where status in ('DISBURSED', 'CLOSED');

update permanent_advances
   set status = 'APPROVED'
 where status in ('DISBURSED', 'CLOSED');

-- No check constraint here, but the column maps to the same enum.
update employee_final_settlement_advance_details
   set advance_status = 'APPROVED'
 where advance_status in ('DISBURSED', 'CLOSED');

alter table short_term_advances drop constraint chk_sta_status;
alter table short_term_advances add constraint chk_sta_status check (status in (
    'PENDING', 'APPROVED', 'REJECTED'));

alter table short_term_advance_batches drop constraint chk_sta_batch_status;
alter table short_term_advance_batches add constraint chk_sta_batch_status check (status in (
    'PENDING', 'MANAGER_APPROVED', 'HR_APPROVED', 'APPROVED', 'REJECTED', 'CANCELLED'));

alter table permanent_advances drop constraint chk_perm_adv_status;
alter table permanent_advances add constraint chk_perm_adv_status check (status in (
    'PENDING', 'MANAGER_APPROVED', 'HR_APPROVED', 'APPROVED', 'REJECTED', 'CANCELLED'));

-- Partial index keyed the "open advance" lookup on DISBURSED.
drop index if exists idx_perm_advances_open;
create index idx_perm_advances_open
    on permanent_advances (employee_id)
    where status = 'APPROVED' and closed_at is null;
