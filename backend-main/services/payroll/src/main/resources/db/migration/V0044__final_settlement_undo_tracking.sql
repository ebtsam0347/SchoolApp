-- ---------------------------------------------------------------------
-- Traceability columns so a rejected final settlement can find and
-- reverse exactly the rows it created at settlement-creation time
-- (leave encashment, permanent advance recovery, short-term advance
-- recovery), without touching rows created by any other settlement or
-- by a regular payroll run.
-- ---------------------------------------------------------------------

ALTER TABLE leave_encashments
    ADD COLUMN final_settlement_id BIGINT;

ALTER TABLE permanent_advance_deductions
    ADD COLUMN final_settlement_id BIGINT;

ALTER TABLE short_term_advances
    ADD COLUMN final_settlement_id BIGINT;
