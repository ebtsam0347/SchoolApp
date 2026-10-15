-- ---------------------------------------------------------------------
-- EmployeeExit becomes a historical table: one row per exit ATTEMPT
-- instead of one permanent row per employee. Without this, a
-- settlement-driven termination that is later REJECTED or CANCELLED
-- has nowhere correct to attach a follow-up settlement's exit record
-- (the old employee_id UNIQUE constraint allows at most one exit row
-- ever, for the employee's whole lifetime).
--
-- Safety: today, employee_exits.employee_id is UNIQUE NOT NULL, so at
-- most one row per employee can exist in any environment right now.
-- Both indexes below are satisfied trivially by "at most one row per
-- employee" — so this migration cannot conflict with any existing data
-- in any environment, without needing a data audit first:
--   * uq_employee_exits_employee_pending is scoped to PENDING rows only
--     -> at most one row per employee today, so at most one PENDING row.
--   * uq_employee_exits_settlement_id is scoped to non-null settlement_id
--     -> at most one row per employee today, so at most one row can
--        carry any given settlement_id.
-- Dropping a UNIQUE constraint can never fail against existing data.
--
-- The real invariant this table must keep enforcing is narrower than
-- "one row ever": an employee must never have two unresolved exits in
-- flight at once. That invariant is already enforced in application
-- code today by ExitService.resign() via
-- existsByEmployeeIdAndStatus(employeeId, PENDING) — proving PENDING is
-- the complete in-flight status set (the other two statuses, COMPLETED
-- and WITHDRAWN, are both resolved end-states). COMPLETED does not need
-- its own database-level mutual-exclusion index: "can't start a new
-- exit while already TERMINATED" is already enforced by
-- employees.employment_status, checked in both ExitService.resign()
-- and ExitService.terminate() — unchanged by this migration.
-- ---------------------------------------------------------------------

ALTER TABLE employee_exits
    DROP CONSTRAINT employee_exits_employee_id_key;

CREATE UNIQUE INDEX uq_employee_exits_employee_pending
    ON employee_exits (employee_id)
    WHERE status = 'PENDING';

CREATE UNIQUE INDEX uq_employee_exits_settlement_id
    ON employee_exits (settlement_id)
    WHERE settlement_id IS NOT NULL;
