-- Shifts: HR wants a stable hand-pickable sort for the shift dropdown
-- rather than alphabetic-by-code (the current repo default). Same
-- convention as cities / grades / qualifications added in employee/V0008.
--
-- Lower numbers sort first. Ties fall back to natural ordering at the
-- repo (code / name). Default 0 keeps newly-added rows at the top
-- until HR sets one — same pattern as payroll.salary_components.

alter table shifts add column display_order int not null default 0;

create index idx_shifts_display_order on shifts (display_order, code);
