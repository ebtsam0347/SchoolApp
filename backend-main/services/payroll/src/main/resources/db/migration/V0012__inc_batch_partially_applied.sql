-- §7.payroll review B1 — IncrementBatchStatus.PARTIALLY_APPLIED was added
-- to the Java enum (apply() flips here when per-line REQUIRES_NEW writes
-- partially fail) but never reached the DB CHECK. Persisting that status
-- raised a 23514 violation, rolled back the outer apply() tx, and dropped
-- the per-line ERROR markers + audit emit — leaving HR with no way to see
-- which employees were actually revised. Drop + re-add the CHECK with
-- the new value included.
alter table increment_batches drop constraint chk_inc_batch_status;
alter table increment_batches add constraint chk_inc_batch_status
    check (status in ('DRAFT','APPLIED','PARTIALLY_APPLIED','CANCELLED'));
