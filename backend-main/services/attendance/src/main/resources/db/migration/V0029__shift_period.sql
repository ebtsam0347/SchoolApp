-- ---------------------------------------------------------------------
-- Day / night marker on a shift. The operator dashboard filters on it
-- (by the shift on each employee's attendance record for the date).
-- Existing shifts that cross midnight (end_time <= start_time — same rule
-- as Shift's derived crosses-midnight flag) start as NIGHT, the rest DAY.
-- ---------------------------------------------------------------------

alter table shifts add column period varchar(8) not null default 'DAY';

alter table shifts
    add constraint ck_shifts_period
    check (period in ('DAY', 'NIGHT'));

update shifts set period = 'NIGHT' where end_time <= start_time;
