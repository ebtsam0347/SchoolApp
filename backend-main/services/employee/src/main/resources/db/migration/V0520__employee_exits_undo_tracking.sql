-- ---------------------------------------------------------------------
-- Reject/undo support for the final-settlement termination flow.
--
-- HR can reject a DRAFT final settlement. Payroll reverses its own
-- financial closing and publishes payroll.final_settlement.rejected;
-- this employee-service must undo everything it closed at settlement
-- creation time. previous_employment_status captures the employment
-- status the employee had immediately before termination, so undo can
-- restore it exactly (rather than assuming ACTIVE).
-- ---------------------------------------------------------------------

ALTER TABLE employee_exits

    ADD COLUMN previous_employment_status VARCHAR(20);
