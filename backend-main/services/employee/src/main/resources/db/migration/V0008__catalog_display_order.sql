-- Catalog ordering: HR wants a stable, hand-pickable sort for the
-- city / grade / qualification dropdowns rather than the alphabetic
-- default the repos use today. Adds a `display_order` column on each
-- and backfills the V0003 seeds with a sensible starting order.
--
-- Lower numbers sort first. Ties fall back to natural ordering at the
-- repo (name / level). Default 0 keeps newly-added rows at the top
-- until HR sets one — same convention as payroll.salary_components.

alter table cities         add column display_order int not null default 0;
alter table grades         add column display_order int not null default 0;
alter table qualifications add column display_order int not null default 0;

-- Seed ordering — Sialkot first (Forward Sports' home base), then the
-- rest in the order they were declared in V0003. Provincial capitals
-- before secondary cities.
update cities set display_order = case code
        when 'SKT' then 10
        when 'LHE' then 20
        when 'GUJ' then 30
        when 'FSD' then 40
        when 'RWP' then 50
        when 'MUL' then 60
        when 'KHI' then 70
        when 'HYD' then 80
        when 'PEW' then 90
        when 'QTA' then 100
        when 'ISB' then 110
        when 'MZD' then 120
        when 'GIL' then 130
        else 0
    end;

-- Qualifications: low-to-high (Matric → PhD), grouped by level.
update qualifications set display_order = case code
        when 'MATRIC'      then 10
        when 'FSC_PRE_ENG' then 20
        when 'FSC_PRE_MED' then 21
        when 'ICOM'        then 22
        when 'ICS'         then 23
        when 'FA'          then 24
        when 'DAE'         then 30
        when 'BSC_CS'      then 40
        when 'BCS'         then 41
        when 'BBA'         then 42
        when 'BCOM'        then 43
        when 'BA'          then 44
        when 'BE'          then 45
        when 'LLB'         then 46
        when 'MBBS'        then 47
        when 'MSC_CS'      then 50
        when 'MBA'         then 51
        when 'MCOM'        then 52
        when 'MA'          then 53
        when 'MS'          then 60
        when 'MPHIL'       then 61
        when 'PHD'         then 70
        else 0
    end;

-- Grades: anchor display_order to level so default sort is bottom-up
-- (Worker → Director). Ties broken by code ascending in repos.
update grades set display_order = level * 10;

create index idx_cities_display_order         on cities         (display_order, name);
create index idx_grades_display_order         on grades         (display_order, level);
create index idx_qualifications_display_order on qualifications (display_order, name);

-- Employee fixed-flag defaults — moved to a separate migration in payroll.
-- Tracked here only so future readers see the shared "every employee is
-- enrolled in EOBI by default" intent applies to both the migration ETL
-- and new-employee onboarding.
