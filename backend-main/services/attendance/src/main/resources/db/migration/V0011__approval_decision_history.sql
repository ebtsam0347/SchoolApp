-- §12.2 — approval decision history.
--
-- Surfaces the "what have I approved/rejected lately" view for any
-- approver. Today the only pending-bound indexes on approval_steps
-- (idx_steps_assignee_pending, idx_steps_role_pending) are partial on
-- status='PENDING' and useless for a "decisions by approver in a date
-- range" query.
--
-- Partial-on-decided_at because un-decided rows (PENDING, NOT_YET,
-- SKIPPED on a cascading reject before they ever ran) carry no
-- decided_at and shouldn't bloat the index.
create index idx_steps_by_decider
    on approval_steps (decided_by, decided_at desc)
 where decided_at is not null;
