-- NEW_HIRE gains a fourth and final stage: HR Approval, mirroring stage 2 of
-- the REPLACEMENT chain (PERMISSION / employee.admin).
--
-- Chains after this migration:
--   REQUISITION_REPLACEMENT: ME -> HR Approval -> D-2
--   REQUISITION_NEW_HIRE:    ME -> D-2 -> Director -> HR Approval
--
-- Only affects requisitions submitted from here on: approval_step rows are
-- snapshotted from this table by ApprovalService.openChain at submit time, so
-- chains already open keep their three stages and still finalize at Director.
delete from approval_workflow_stage where request_type in ('REQUISITION_REPLACEMENT', 'REQUISITION_NEW_HIRE');


insert into approval_workflow_stage
    (request_type, stage_no, resolver_kind, approve_permission, resolver_target, label, created_by, updated_by)
values
    ('REQUISITION_REPLACEMENT', 1, 'DEPARTMENT_HEAD',  null,              'ME',        'Manufacturing Excellence Review', 'system', 'system'),
    ('REQUISITION_REPLACEMENT', 2, 'PERMISSION',       'employee.admin',   null,       'HR Approval', 'system', 'system'),
    ('REQUISITION_REPLACEMENT', 3, 'GRADE',            null,              'D-2',       'D-2 Sign-off',                    'system', 'system'),

    ('REQUISITION_NEW_HIRE',    1, 'DEPARTMENT_HEAD',  null,              'ME',        'Manufacturing Excellence Review', 'system', 'system'),
    ('REQUISITION_NEW_HIRE',    2, 'PERMISSION',       'employee.admin',   null,       'HR Approval', 'system', 'system'),
    ('REQUISITION_NEW_HIRE',    3, 'GRADE',            null,              'D-2',       'D-2 Sign-off',                    'system', 'system'),
    ('REQUISITION_NEW_HIRE',    4, 'GRADE',            null,              'Director',  'Director Approval',               'system', 'system');
