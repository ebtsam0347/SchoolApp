-- §7.payroll review B3 — bonus-sourced off-cycle rows had no idempotency
-- guard. The applyInstallment saga fans out per-employee REQUIRES_NEW
-- writes and only flips the installment to APPLIED in a closing tx; a
-- retry between the per-employee commits and the closing commit would
-- duplicate every off-cycle row. Add a partial unique index keyed on
-- (source_event_id, source_installment_id, employee_id) so the second
-- pass trips the constraint and the BatchRunner records it as a failed
-- row — no silent doubling of bonus payments.
--
-- Partial WHERE clause: HR-recorded rows have source_event_id = null
-- (chk_ocp_source ensures both source columns move together) and must
-- stay free to duplicate at HR's discretion. The index only covers the
-- bonus-sourced rows.
create unique index uq_ocp_bonus_source
    on off_cycle_payments (source_event_id, source_installment_id, employee_id)
    where source_event_id is not null;
