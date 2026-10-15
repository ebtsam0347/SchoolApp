-- ---------------------------------------------------------------------
-- Allow REINSTATED as a valid employee_history.change_reason value —
-- used when TerminationSaga.undoTermination() reopens the history row
-- that was closed on EXIT.
-- ---------------------------------------------------------------------

ALTER TABLE employee_history
    DROP CONSTRAINT chk_history_change_reason;

ALTER TABLE employee_history
    ADD CONSTRAINT chk_history_change_reason CHECK (
        change_reason IS NULL OR change_reason IN (
            'ONBOARD', 'TRANSFER', 'PROMOTION', 'DEMOTION',
            'DEPARTMENT_CHANGE', 'MANAGER_CHANGE', 'SECTION_CHANGE', 'EXIT',
            'REINSTATED'
        )
    );
