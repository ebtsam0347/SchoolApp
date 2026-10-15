-- B5 — guard against an employee being set as their own manager. Migration
-- from WebHRMS slipped at least one self-loop through (Jawad Khalid 10/14790)
-- because the bulk UPDATE on manager_id didn't gate on emap.target_id <>
-- mmap.target_id. Backfill any existing rows by nulling the loop, then add
-- a CHECK so write paths can't reintroduce one. The service-side
-- ManagerCycleChecker already enforces this on /assignment-changes/manager;
-- this is the DB-level safety net for direct writes / future bulk imports.
update employee.employees
set manager_id = null,
    updated_at = updated_at
where manager_id = id;

alter table employee.employees
    add constraint employees_manager_id_not_self
    check (manager_id is distinct from id);
