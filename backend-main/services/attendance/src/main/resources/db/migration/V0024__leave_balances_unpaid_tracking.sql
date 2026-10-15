-- Add columns to track leave balance entries created from annual leave unpaid transactions
-- This allows HR to identify and undo carry-over entries when toggling payment status

ALTER TABLE leave_balances
    ADD COLUMN is_annual_leave_unpaid_carry BOOLEAN DEFAULT FALSE,
    ADD COLUMN annual_leave_transaction_id BIGINT;

-- Column meanings:
-- is_annual_leave_unpaid_carry = TRUE = created from annual leave unpaid transaction
-- annual_leave_transaction_id = references payroll.annual_leave_transaction for undoing
