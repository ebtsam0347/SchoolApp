-- Off-Cycle Payments — HR -> Accounts lifecycle.
--
-- Two authorities, three states:
--
--   HR creates (creation IS the HR authorization — no separate submit /
--   approve step), edits or cancels while HR_APPROVED; Accounts pays.
--
--     CREATE -> HR_APPROVED -+- HR edit   -> HR_APPROVED
--                            +- HR cancel -> CANCELLED  (immutable)
--                            +- Accounts  -> PAID       (immutable)
--
-- Until now the table had no lifecycle at all: a row was created as a
-- finished fact and stayed editable and hard-deletable forever. The only
-- immutability rule was `source_event_id is not null`, which is a bonus
-- provenance marker, not a lifecycle state.
--
-- BACKFILL: every existing row becomes PAID. Those rows were recorded by
-- HR as payments that had already happened, so HR_APPROVED would drop
-- years of history into the Accounts payment queue as new work. PAID also
-- freezes them, which is the correct end state for a completed payment.
--
-- `hr_approved_by/at` and `paid_by/at` are deliberately left NULL on
-- backfilled rows: the old schema recorded no approval or payment actor,
-- and inventing one would put a false name on a financial record. The
-- pre-existing `created_by` / `created_at` audit columns remain the only
-- honest provenance for historical rows.

alter table off_cycle_payments
    add column status          varchar(16),
    add column hr_approved_by  bigint,
    add column hr_approved_at  timestamp,
    add column paid_by         bigint,
    add column paid_at         timestamp,
    -- NOT NULL DEFAULT 0 so existing rows get a usable @Version seed; a
    -- nullable version column makes Hibernate treat every legacy row as
    -- unversioned and blow up on the first update.
    add column version         bigint not null default 0;

update off_cycle_payments set status = 'PAID' where status is null;

alter table off_cycle_payments
    alter column status set not null;

alter table off_cycle_payments
    add constraint chk_ocp_status check (status in ('HR_APPROVED', 'PAID', 'CANCELLED'));

-- Accounts' work queue is `status = 'HR_APPROVED'`; after the backfill it
-- is empty and stays small, so a plain b-tree on status is enough.
create index idx_ocp_status on off_cycle_payments (status);

-- No DEFAULT on `status`: an insert that forgets to set it should fail
-- loudly rather than silently minting a PAID financial record.
