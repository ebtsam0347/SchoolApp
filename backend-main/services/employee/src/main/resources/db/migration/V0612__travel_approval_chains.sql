-- ============================================================
-- Travel approval chains — align with the company process.
--
-- Reuses the exact resolver kinds / permissions already used by payroll's
-- Permanent/Long-Term Advance chain (payroll V0042):
--   MANAGER  stage  -> hierarchy resolver (submitter's manager), no permission
--   HR       stage  -> PERMISSION  travel.approve
--   ACCOUNTS stage  -> PERMISSION  travel.advance.approve   (Travel Advance only)
--
-- Final chains:
--   TRAVEL_REQUEST   stage 1 MANAGER            'Manager Approval'
--                    stage 2 PERMISSION         'HR Approval'        travel.approve
--   TRAVEL_ADVANCE   stage 1 MANAGER            'Manager Approval'
--                    stage 2 PERMISSION         'HR Approval'        travel.approve
--                    stage 3 PERMISSION         'Accounts Approval'  travel.advance.approve
--
-- TRAVEL_REQUEST is already MANAGER -> HR from V0611 and is re-asserted here
-- unchanged so the whole Travel config is described in one place.
-- TRAVEL_ADVANCE gains the HR stage ahead of the existing accounts pool.
--
-- Editing a chain never disturbs in-flight requests: approval_steps are
-- snapshot at submit time. Old rows are DEACTIVATED (not deleted) for
-- audit; the partial unique index uq_workflow_stage_active only constrains
-- active rows, so the deactivated rows do not collide with the new ones.
-- Same technique as payroll V0042__payroll_advance_manager_stage.sql.
-- ============================================================

update approval_workflow_stage
    set active = false, updated_by = 'system'
    where request_type in ('TRAVEL_REQUEST', 'TRAVEL_ADVANCE') and active;

insert into approval_workflow_stage
    (request_type, stage_no, resolver_kind, approve_permission, label, created_by, updated_by)
values
    ('TRAVEL_REQUEST', 1, 'MANAGER',    null,                     'Manager Approval',  'system', 'system'),
    ('TRAVEL_REQUEST', 2, 'PERMISSION', 'travel.approve',         'HR Approval',        'system', 'system'),
    ('TRAVEL_ADVANCE', 1, 'MANAGER',    null,                     'Manager Approval',  'system', 'system'),
    ('TRAVEL_ADVANCE', 2, 'PERMISSION', 'travel.approve',         'HR Approval',        'system', 'system'),
    ('TRAVEL_ADVANCE', 3, 'PERMISSION', 'travel.advance.approve', 'Accounts Approval', 'system', 'system');
