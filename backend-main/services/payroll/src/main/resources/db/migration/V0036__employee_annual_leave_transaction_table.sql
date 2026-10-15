CREATE TABLE annual_leave_transaction (
      id BIGSERIAL PRIMARY KEY,

      employee_id BIGINT NOT NULL,
      employee_code      varchar(32)  not null,
      calculation_month DATE NOT NULL, -- Always 1st day of month (e.g., '2026-07-01')

        -- Salary snapshot
      basic_salary bigint NOT NULL,
      per_day_salary bigint NOT NULL,

       -- annual leave cycle dates
      cycle_start date not null,
      cycle_end date not null,

        -- Leave days
      eligible_leave_days bigint NOT NULL,
      availed_leave_days bigint DEFAULT 0,
      payable_leave_days bigint NOT NULL, -- eligible - availed

        -- Amounts
      leave_amount bigint NOT NULL, -- (basic salary/26) * annual leave quota paid days (14)
      gross_annual_leave_amount bigint NOT NULL, -- payable_days * per_day_salary
      deduction_as_long_term_advance bigint DEFAULT 0,
      net_amount bigint NOT NULL, -- gross - deduction

        -- Status & Payment
      payment_status BOOLEAN DEFAULT FALSE,
      lock_status BOOLEAN DEFAULT FALSE,

        -- Audit
    created_at timestamptz  not null default now(),
    updated_at timestamptz  not null default now(),
    created_by VARCHAR(64) not null,
    updated_by VARCHAR(64) not null,
    ip_address INET,

    UNIQUE(employee_id, calculation_month)
);
CREATE INDEX idx_annual_leave_transaction_employee_id
    ON annual_leave_transaction(employee_id);

CREATE INDEX idx_annual_leave_transaction_calculation_month
    ON annual_leave_transaction(calculation_month);

CREATE INDEX idx_annual_leave_transaction_payment_status
    ON annual_leave_transaction(calculation_month, payment_status);

CREATE INDEX idx_annual_leave_transaction_lock_status
    ON annual_leave_transaction(calculation_month, lock_status);

CREATE INDEX idx_annual_leave_transaction_payment_status_lock_status
    ON annual_leave_transaction(calculation_month, payment_status, lock_status);

CREATE INDEX idx_annual_leave_transaction_employee_month
    ON annual_leave_transaction(employee_id, calculation_month ASC);
