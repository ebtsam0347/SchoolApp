ALTER TABLE bonus_transaction
DROP CONSTRAINT chk_bonus_transaction_payment_mode;

ALTER TABLE bonus_transaction
    ADD CONSTRAINT chk_bonus_transaction_payment_mode
        CHECK (payment_mode IN ('BANK_TRANSFER', 'CASH'));