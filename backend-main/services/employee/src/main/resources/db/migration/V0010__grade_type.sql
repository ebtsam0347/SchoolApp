-- Coarse role bucket per grade. Drives payroll's grade-conditional
-- canteen rule (management → weekly, supervisor/labour → per-day).
-- Default seeded from the legacy `level` so HR doesn't have to retag
-- every grade; they can override per row from the catalog UI.

alter table grades
    add column grade_type varchar(16);

update grades
   set grade_type = case
       when level >= 4 then 'MANAGEMENT'
       when level >= 2 then 'SUPERVISOR'
       else 'LABOUR'
   end;

alter table grades
    alter column grade_type set not null,
    alter column grade_type set default 'LABOUR';
