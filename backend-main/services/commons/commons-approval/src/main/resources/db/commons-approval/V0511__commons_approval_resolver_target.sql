-- Target key for DEPARTMENT_HEAD (department code) and GRADE (grade code)
-- resolver kinds. Null for MANAGER/PERMISSION stages, which keep using
-- assigned_to / approve_permission respectively.
alter table approval_workflow_stage add column if not exists resolver_target varchar(64);
alter table approval_steps add column if not exists resolver_target varchar(64);

-- Retag any in-flight REQUISITION steps (submitted under the old single
-- shared chain) to the new per-type request_type so they keep resolving
-- against the workflow config seeded in employee V0016. No-op if none exist.
-- Guarded: employee_requisitions only exists in the employee schema, but
-- this migration ships in commons-approval and runs in every schema that
-- has approval_steps (e.g. attendance).
do $$
begin
    if to_regclass('employee_requisitions') is not null then
        update approval_steps s
        set request_type = case
                when (select r.type from employee_requisitions r where r.id = s.request_id) = 'REPLACEMENT'
                    then 'REQUISITION_REPLACEMENT'
                else 'REQUISITION_NEW_HIRE'
            end
        where s.request_type = 'REQUISITION';
    end if;
end $$;
