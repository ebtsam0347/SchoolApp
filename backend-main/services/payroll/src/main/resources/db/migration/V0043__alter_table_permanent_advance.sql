ALTER TABLE permanent_advances
    ADD COLUMN fiscal_year_code VARCHAR(7) NOT NULL,
    ADD COLUMN bonus_offset2 NUMERIC(15,2) NULL DEFAULT 0,
    ADD COLUMN annual_leave_month DATE NULL,
    ADD CONSTRAINT fk_permanent_advances_fiscal_year
        FOREIGN KEY (fiscal_year_code)
        REFERENCES fiscal_years(code);