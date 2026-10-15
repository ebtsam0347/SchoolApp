-- ============================================================
-- Revert off_cycle_payments source constraint
-- Remove settlement_id requirement and restore previous constraint
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
                )
                OR
            (
                source_event_id IS NOT NULL
                    AND source_installment_id IS NOT NULL
                )
            );