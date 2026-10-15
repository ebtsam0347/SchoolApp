-- ---------------------------------------------------------------------
-- Optional IN / OUT direction on a punch. An operator adding a punch to a
-- record says whether it is the day's check-in or check-out; the recompute
-- then puts it there even when other punches are earlier / later. Null on
-- every existing row and on device / web punches — those keep the
-- earliest-is-in / latest-is-out rule.
-- ---------------------------------------------------------------------

alter table attendance_punches add column direction varchar(8);

alter table attendance_punches
    add constraint ck_attendance_punches_direction
    check (direction in ('IN', 'OUT'));
