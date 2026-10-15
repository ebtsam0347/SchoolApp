-- Two small refinements surfaced by the legacy WebHRMS catalog comparison:
--   1. Designation `category` (DIRECT vs INDIRECT) — Pakistan manufacturing
--      convention separating production-line workers from admin/office.
--      Drives headcount splits and OT-eligibility defaults. Replaces the
--      need for a separate employee_categories catalog: every employee's
--      category is implied by their designation.
--   2. `VERBAL_WARNING` joins the disciplinary escalation ladder below
--      FIRST_WARNING. Legacy used 4 levels (Verbal → 1st → 2nd → Final);
--      we previously had 3.

alter table designations
    add column category varchar(16);

alter table designations
    add constraint chk_designation_category check (
        category is null or category in ('DIRECT', 'INDIRECT')
    );

alter table disciplinary_actions
    drop constraint chk_disc_level;

alter table disciplinary_actions
    add constraint chk_disc_level check (
        level in ('VERBAL_WARNING', 'FIRST_WARNING', 'SECOND_WARNING', 'FINAL_WARNING')
    );
