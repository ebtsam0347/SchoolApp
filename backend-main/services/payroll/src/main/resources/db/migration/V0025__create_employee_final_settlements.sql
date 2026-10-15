CREATE TABLE employee_final_settlements
(
    id                       BIGSERIAL PRIMARY KEY,

    --------------------------------------------------------------------
    -- Employee Snapshot
    --------------------------------------------------------------------
    employee_id              BIGINT         NOT NULL,
    employee_code            VARCHAR(30)    NOT NULL,
    employee_name            VARCHAR(150)   NOT NULL,

    department               VARCHAR(100),
    section                  VARCHAR(100),
    designation              VARCHAR(100),
    grade                    VARCHAR(30),
    grade_type               VARCHAR(30),

    hire_date                DATE,
    gratuity_start_date      DATE,
    last_working_day         DATE           NOT NULL,

    --------------------------------------------------------------------
    -- Salary Snapshot
    --------------------------------------------------------------------
    monthly_basic_salary     NUMERIC(18, 2) NOT NULL DEFAULT 0,
    earned_basic_salary      NUMERIC(18, 2) NOT NULL DEFAULT 0,
    allowances               NUMERIC(18, 2) NOT NULL DEFAULT 0,
    gross_salary             NUMERIC(18, 2) NOT NULL DEFAULT 0,

    --------------------------------------------------------------------
    -- Gratuity
    --------------------------------------------------------------------
    gratuity_eligible        BOOLEAN        NOT NULL DEFAULT FALSE,
    gratuity_completed_years INTEGER        NOT NULL DEFAULT 0,
    gratuity_amount          NUMERIC(18, 2) NOT NULL DEFAULT 0,

    --------------------------------------------------------------------
    -- Advances
    --------------------------------------------------------------------
    permanent_advance        NUMERIC(18, 2) NOT NULL DEFAULT 0,
    short_term_advance       NUMERIC(18, 2) NOT NULL DEFAULT 0,
    total_advance            NUMERIC(18, 2) NOT NULL DEFAULT 0,

    --------------------------------------------------------------------
    -- Fair Price Shop
    --------------------------------------------------------------------
    fair_price_shop_amount   NUMERIC(18, 2) NOT NULL DEFAULT 0,

    --------------------------------------------------------------------
    -- Settlement Totals
    --------------------------------------------------------------------
    gross_settlement         NUMERIC(18, 2) NOT NULL DEFAULT 0,
    total_deductions         NUMERIC(18, 2) NOT NULL DEFAULT 0,
    tax_amount               NUMERIC(18, 2) NOT NULL DEFAULT 0,
    net_settlement           NUMERIC(18, 2) NOT NULL DEFAULT 0,

    --------------------------------------------------------------------
    -- HR Remarks
    --------------------------------------------------------------------
    remarks                  TEXT,

    --------------------------------------------------------------------
    -- Workflow
    --------------------------------------------------------------------
    status                   VARCHAR(30)    NOT NULL,

    --------------------------------------------------------------------
    -- Audit
    --------------------------------------------------------------------
    created_by               BIGINT,
    approved_by              BIGINT,

    approved_at              TIMESTAMP,

    created_at               TIMESTAMP      NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at               TIMESTAMP      NOT NULL DEFAULT CURRENT_TIMESTAMP,

    version                  BIGINT         NOT NULL DEFAULT 0
);

CREATE INDEX idx_employee_final_settlement_employee
    ON employee_final_settlements (employee_id);

CREATE INDEX idx_employee_final_settlement_code
    ON employee_final_settlements (employee_code);

CREATE INDEX idx_employee_final_settlement_status
    ON employee_final_settlements (status);

CREATE INDEX idx_employee_final_settlement_last_working_day
    ON employee_final_settlements (last_working_day);