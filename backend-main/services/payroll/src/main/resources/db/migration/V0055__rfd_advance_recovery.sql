-- RFD advance recovery (Audit Finding 6). RfdAdvance rows track cash
-- given to an employee in excess of what they were owed (bonus payouts
-- rounded up to the nearest 500-rupee note) — a real, if small,
-- receivable that previously had no recovery path anywhere. Recovered
-- in full at final settlement, mirroring short_term_advances.

ALTER TABLE rfd_advance
    ADD COLUMN recovered boolean NOT NULL DEFAULT false,
    ADD COLUMN final_settlement_id bigint;

CREATE INDEX idx_rfd_advance_employee_recovered
    ON rfd_advance (employee_id, recovered);

ALTER TABLE employee_final_settlements
    ADD COLUMN rfd_advance numeric(18,2) NOT NULL DEFAULT 0;
