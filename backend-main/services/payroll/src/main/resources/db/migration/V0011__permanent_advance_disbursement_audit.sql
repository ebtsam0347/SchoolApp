-- §7.payroll #38 — record who disbursed and when. Previously the
-- disburse() flow only flipped status, leaving HR / accounts to dig
-- into audit_events for "when did money actually move."
--
-- New columns are nullable so the migration is non-blocking against
-- existing DISBURSED rows. New disbursements populate both fields;
-- legacy rows can be backfilled from audit later if needed (cross-
-- schema, runs out-of-band).
alter table permanent_advances
    add column disbursed_at timestamp null,
    add column disbursed_by bigint null;
