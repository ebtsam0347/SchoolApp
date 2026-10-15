ALTER TABLE payroll.permanent_advance_deductions
    ADD COLUMN employee_id bigint not null ,
    ALTER COLUMN advance_id DROP NOT NULL;