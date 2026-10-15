
-- Adds a Manager-approval stage ahead of HR/Accounts for both advance
-- types. Only the active workflow config changes — in-flight approval_steps
-- keep their already-snapshotted 2-stage layout (editing a chain never
-- disturbs in-flight requests, same as employee's V0016).

-- New intermediate business status (Manager done, HR pending) needs to be
-- whitelisted on both tables' status check constraints.
alter table permanent_advances drop constraint chk_perm_adv_status;
alter table permanent_advances add constraint chk_perm_adv_status check (status in (
    'PENDING','MANAGER_APPROVED','HR_APPROVED','APPROVED','REJECTED','DISBURSED','CLOSED','CANCELLED'));

alter table short_term_advance_batches drop constraint chk_sta_batch_status;
alter table short_term_advance_batches add constraint chk_sta_batch_status check (status in (
    'PENDING','MANAGER_APPROVED','HR_APPROVED','APPROVED','REJECTED','DISBURSED','CLOSED','CANCELLED'));

-- Deactivate rather than delete — keeps the old 2-stage config around for
-- audit/debugging. The partial unique index on (request_type, stage_no)
-- where active only constrains the active set, so the deactivated rows
-- don't collide with the new ones inserted below.
update approval_workflow_stage
    set active = false, updated_by = 'system'
    where request_type in ('PERMANENT_ADVANCE', 'SHORT_TERM_ADVANCE') and active;

insert into approval_workflow_stage
    (request_type, stage_no, resolver_kind, approve_permission, label, created_by, updated_by)
values
    ('PERMANENT_ADVANCE',  1, 'MANAGER',    null,                       'Manager Approval',  'system', 'system'),
    ('PERMANENT_ADVANCE',  2, 'PERMISSION', 'payroll.advance.hr',       'HR Approval',       'system', 'system'),
    ('PERMANENT_ADVANCE',  3, 'PERMISSION', 'payroll.advance.accounts', 'Accounts Approval', 'system', 'system'),
    ('SHORT_TERM_ADVANCE', 1, 'MANAGER',    null,                       'Manager Approval',  'system', 'system'),
    ('SHORT_TERM_ADVANCE', 2, 'PERMISSION', 'payroll.advance.hr',       'HR Approval',       'system', 'system'),
    ('SHORT_TERM_ADVANCE', 3, 'PERMISSION', 'payroll.advance.accounts', 'Accounts Approval', 'system', 'system');
