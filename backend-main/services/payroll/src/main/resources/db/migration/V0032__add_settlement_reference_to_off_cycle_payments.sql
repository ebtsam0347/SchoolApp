-- settlement reference to off_cycle_payments table as foreign key
ALTER TABLE off_cycle_payments
    ADD COLUMN settlement_id BIGINT;

ALTER TABLE off_cycle_payments
    ADD CONSTRAINT fk_ocp_settlement
        FOREIGN KEY (settlement_id)
            REFERENCES employee_final_settlements (id);


-- ============================================================
-- Replace old source constraint & add new source constraint to off_cycle_payments table
-- ============================================================

ALTER TABLE off_cycle_payments
DROP
CONSTRAINT chk_ocp_source;

ALTER TABLE off_cycle_payments
    ADD CONSTRAINT chk_ocp_source
        CHECK (

            (
                source_event_id IS NULL
                    AND source_installment_id IS NULL
                    AND settlement_id IS NOT NULL
                )
                OR
            (
                source_event_id IS NOT NULL
                    AND source_installment_id IS NOT NULL
                    AND settlement_id IS NULL
                )

            );