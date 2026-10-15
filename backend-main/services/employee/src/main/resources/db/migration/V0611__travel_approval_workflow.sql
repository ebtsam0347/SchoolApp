-- ============================================================
-- Travel Module - default commons-approval chains
--
-- approval_workflow_stage is created by commons-approval V0510
-- (CREATE TABLE IF NOT EXISTS). This migration only seeds the
-- Travel module's default chains; HR re-configures them at runtime
-- via /approval-workflows/<TYPE>. Editing a chain never disturbs
-- in-flight requests - approval_steps are snapshot at submit.
--
-- Default chains:
--   TRAVEL_REQUEST     stage 1 MANAGER
--                      stage 2 PERMISSION  travel.approve            (HR)
--   TRAVEL_ADVANCE     stage 1 MANAGER
--                      stage 2 PERMISSION  travel.advance.approve    (Finance)
--   TRAVEL_SETTLEMENT  stage 1 PERMISSION  travel.settlement.approve (Finance/HR)
--
-- MANAGER stages resolve against the submitter's manager at submit
-- time (reuses the employee-service ManagerResolver bean).
-- PERMISSION stages are role-only pools keyed by approve_permission.
-- ============================================================

insert into approval_workflow_stage
    (request_type, stage_no, resolver_kind, approve_permission, label, created_by, updated_by)
values
    ('TRAVEL_REQUEST',    1, 'MANAGER',    null,                        'Manager Approval',    'system', 'system'),
    ('TRAVEL_REQUEST',    2, 'PERMISSION', 'travel.approve',            'HR Approval',         'system', 'system'),
    ('TRAVEL_ADVANCE',    1, 'MANAGER',    null,                        'Manager Approval',    'system', 'system'),
    ('TRAVEL_ADVANCE',    2, 'PERMISSION', 'travel.advance.approve',    'Finance Approval',    'system', 'system'),
    ('TRAVEL_SETTLEMENT', 1, 'PERMISSION', 'travel.settlement.approve', 'Settlement Approval', 'system', 'system');
