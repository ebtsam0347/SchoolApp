-- Off-Cycle Payments — point the HR-facing type at the employee-service
-- master, and separate it from the internal transaction classification.
--
-- Two concepts that were conflated in one free-text `category` column:
--
--   HR business type   -> employee.off_cycle_payment_type (HR maintains it:
--                         adds types, renames them, activates/deactivates).
--                         Referenced here by id + a name snapshot.
--   System source      -> HR | BONUS | FINAL_SETTLEMENT. Set by payroll code,
--                         never by a request body, never by the HR master.
--
-- The column keeps the name `category` on purpose: renaming it would touch
-- five JPQL sites, the XLSX report, the composite row and the audit key for
-- no behavioral gain. The Java side reads it as OffCyclePaymentSource.
--
-- Why the HR master cannot drive `category`: SettlementBonusCalculator sums
-- BONUS rows to work out how much of a bonus event a leaver has already been
-- paid. If a master type named "Bonus" produced category='BONUS', HR could
-- change settlement math by naming a dropdown entry. So an HR-created row is
-- always 'HR', whatever its type is called.

alter table off_cycle_payments
    -- Catalog reference into employee-service; not enforced cross-schema
    -- (separate service, separate schema) — same convention as
    -- short_term_advance_batches.department_id in V0007.
    add column off_cycle_payment_type_id   bigint,
    -- What HR saw when they chose it. Master renames must not rewrite
    -- history; the id still resolves to the current name for anyone who
    -- wants it. Same pattern as employee_final_settlements.employee_name.
    add column off_cycle_payment_type_name varchar(100);

-- ---------------------------------------------------------------------
-- Backfill. Exhaustive by construction — no value can fall through.
--
-- BONUS and FINAL_SETTLEMENT rows keep their classification untouched, so
-- every bonus-settlement figure is identical the day after this migration.
-- That deliberately includes any BONUS row an HR user typed by hand before
-- the create API stopped accepting a free-text category: reclassifying it
-- to 'HR' would silently drop it out of SettlementBonusCalculator and
-- change what a leaver is owed. Provenance stays readable either way —
-- source_event_id is non-null on system-generated rows and null on
-- hand-entered ones (chk_ocp_source keeps the pair consistent).
--
-- Everything else — EXTRA, SPECIAL_OT, OTHER, and any value a deployed
-- environment holds that this repo has never seen — becomes 'HR' with its
-- original label preserved verbatim in the name snapshot.
-- ---------------------------------------------------------------------
update off_cycle_payments
   set off_cycle_payment_type_name = category,
       category = 'HR'
 where category not in ('BONUS', 'FINAL_SETTLEMENT');

-- Applied AFTER the backfill: legacy EXTRA / SPECIAL_OT / OTHER rows would
-- fail it beforehand and abort the deployment mid-flight.
alter table off_cycle_payments
    add constraint chk_ocp_category check (category in ('HR', 'BONUS', 'FINAL_SETTLEMENT'));

-- One-directional on purpose. System rows never carry a master type, so
-- that half is a hard rule. The HR half is not: rows created before this
-- migration were never given a master selection, and NULL is the honest
-- record of "HR never picked one" rather than missing data awaiting repair.
-- New HR rows are forced to carry an id by OffCyclePaymentService.create.
alter table off_cycle_payments
    add constraint chk_ocp_system_has_no_type check (
        category = 'HR' or off_cycle_payment_type_id is null
    );

create index idx_ocp_type on off_cycle_payments (off_cycle_payment_type_id)
    where off_cycle_payment_type_id is not null;
