-- §7.attendance — repair approval_steps.assigned_to that ApproverResolver
-- wrote with the manager's employee_id instead of their user_id. The bug
-- shipped with the original attendance system; in-flight MANAGER stages
-- (PENDING / NOT_YET) end up keyed off the wrong id, so the manager's
-- inbox is empty, canAct() refuses the request, and the LEAVE_APPLIED
-- notification routes to a non-existent user. Code fix is in
-- ApproverResolver; this migration repairs rows already on disk.
--
-- Strategy: rewrite assigned_to → that manager's user_id for every open
-- MANAGER step where assigned_to equals the request employee's manager_id
-- AND that manager has a linked auth user. Closed stages (APPROVED /
-- REJECTED / SKIPPED) are left alone — decided_by has its own provenance.
--
-- Cross-schema reads here are deliberate. Runtime code respects the
-- schema-per-service rule; one-shot Flyway data repair predates that.

with affected as (
    select s.id, e_mgr.user_id as new_assigned_to
      from attendance.approval_steps s
      join attendance.leave_requests r on r.id = s.request_id
      join employee.employees e_emp on e_emp.id = r.employee_id
      join employee.employees e_mgr on e_mgr.id = e_emp.manager_id
     where s.request_type = 'LEAVE'
       and s.stage_role  = 'MANAGER'
       and s.status      in ('PENDING','NOT_YET')
       and s.assigned_to = e_emp.manager_id
       and e_mgr.user_id is not null
)
update attendance.approval_steps s
   set assigned_to = a.new_assigned_to,
       updated_at  = now(),
       updated_by  = 'flyway:V0017'
  from affected a
 where s.id = a.id;

with affected as (
    select s.id, e_mgr.user_id as new_assigned_to
      from attendance.approval_steps s
      join attendance.overtime_requests r on r.id = s.request_id
      join employee.employees e_emp on e_emp.id = r.employee_id
      join employee.employees e_mgr on e_mgr.id = e_emp.manager_id
     where s.request_type = 'OVERTIME'
       and s.stage_role  = 'MANAGER'
       and s.status      in ('PENDING','NOT_YET')
       and s.assigned_to = e_emp.manager_id
       and e_mgr.user_id is not null
)
update attendance.approval_steps s
   set assigned_to = a.new_assigned_to,
       updated_at  = now(),
       updated_by  = 'flyway:V0017'
  from affected a
 where s.id = a.id;

with affected as (
    select s.id, e_mgr.user_id as new_assigned_to
      from attendance.approval_steps s
      join attendance.regularization_requests r on r.id = s.request_id
      join employee.employees e_emp on e_emp.id = r.employee_id
      join employee.employees e_mgr on e_mgr.id = e_emp.manager_id
     where s.request_type = 'REGULARIZATION'
       and s.stage_role  = 'MANAGER'
       and s.status      in ('PENDING','NOT_YET')
       and s.assigned_to = e_emp.manager_id
       and e_mgr.user_id is not null
)
update attendance.approval_steps s
   set assigned_to = a.new_assigned_to,
       updated_at  = now(),
       updated_by  = 'flyway:V0017'
  from affected a
 where s.id = a.id;

with affected as (
    select s.id, e_mgr.user_id as new_assigned_to
      from attendance.approval_steps s
      join attendance.short_leave_requests r on r.id = s.request_id
      join employee.employees e_emp on e_emp.id = r.employee_id
      join employee.employees e_mgr on e_mgr.id = e_emp.manager_id
     where s.request_type = 'SHORT_LEAVE'
       and s.stage_role  = 'MANAGER'
       and s.status      in ('PENDING','NOT_YET')
       and s.assigned_to = e_emp.manager_id
       and e_mgr.user_id is not null
)
update attendance.approval_steps s
   set assigned_to = a.new_assigned_to,
       updated_at  = now(),
       updated_by  = 'flyway:V0017'
  from affected a
 where s.id = a.id;
