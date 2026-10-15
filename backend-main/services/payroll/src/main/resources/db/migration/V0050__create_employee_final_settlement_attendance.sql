CREATE TABLE employee_final_settlement_attendance
(
    id BIGSERIAL PRIMARY KEY,

    -------------------------------------------------------------------
    -- Parent Settlement
    -------------------------------------------------------------------
    settlement_id BIGINT NOT NULL
        REFERENCES employee_final_settlements(id)
            ON DELETE CASCADE,

    -------------------------------------------------------------------
    -- Employee Reference (snapshot only, not the join key)
    -------------------------------------------------------------------
    employee_id BIGINT NOT NULL,

    -------------------------------------------------------------------
    -- Period Snapshot
    -------------------------------------------------------------------
    period_from DATE NOT NULL,

    period_to DATE NOT NULL,

    -------------------------------------------------------------------
    -- Attendance Snapshot
    -------------------------------------------------------------------
    present_days BIGINT NOT NULL DEFAULT 0,

    half_days BIGINT NOT NULL DEFAULT 0,

    leave_days BIGINT NOT NULL DEFAULT 0,

    paid_leave_days BIGINT NOT NULL DEFAULT 0,

    unpaid_leave_days BIGINT NOT NULL DEFAULT 0,

    absent_days BIGINT NOT NULL DEFAULT 0,

    weekend_days BIGINT NOT NULL DEFAULT 0,

    holiday_days BIGINT NOT NULL DEFAULT 0,

    -------------------------------------------------------------------
    -- Audit
    -------------------------------------------------------------------
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_final_settlement_attendance_settlement
    ON employee_final_settlement_attendance(settlement_id);

CREATE INDEX idx_final_settlement_attendance_employee
    ON employee_final_settlement_attendance(employee_id);
