-- Replace the single shared REQUISITION chain with two type-specific chains.
-- REPLACEMENT: Manufacturing Excellence dept head -> HR Admin -> D-2 grade.
-- NEW_HIRE:    Manufacturing Excellence dept head -> D-2 grade -> Director grade.
--
-- The resolver_target column is normally added by commons-approval V0511,
-- but V0016 < V0511 so we add it here too (IF NOT EXISTS) — same reasoning
-- as V0013 creating approval_workflow_stage ahead of V0510.
alter table approval_workflow_stage add column if not exists resolver_target varchar(64);

delete from approval_workflow_stage where request_type = 'REQUISITION';

insert into approval_workflow_stage
    (request_type, stage_no, resolver_kind, approve_permission, resolver_target, label, created_by, updated_by)
values
    ('REQUISITION_REPLACEMENT', 1, 'DEPARTMENT_HEAD', null,              'ME',       'Manufacturing Excellence Review', 'system', 'system'),
    ('REQUISITION_REPLACEMENT', 2, 'PERMISSION',       'employee.admin', null,       'HR Approval',                     'system', 'system'),
    ('REQUISITION_REPLACEMENT', 3, 'GRADE',            null,              'D-2',      'D-2 Sign-off',                    'system', 'system'),

    ('REQUISITION_NEW_HIRE',    1, 'DEPARTMENT_HEAD', null,              'ME',       'Manufacturing Excellence Review', 'system', 'system'),
    ('REQUISITION_NEW_HIRE',    2, 'GRADE',            null,              'D-2',      'D-2 Sign-off',                    'system', 'system'),
    ('REQUISITION_NEW_HIRE',    3, 'GRADE',            null,              'Director', 'Director Approval',               'system', 'system');
