-- Remove settlement_id column
ALTER TABLE off_cycle_payments
DROP
COLUMN IF EXISTS settlement_id;