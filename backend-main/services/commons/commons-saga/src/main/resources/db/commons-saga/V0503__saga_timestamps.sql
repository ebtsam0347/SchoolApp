-- §0.5.B — see commons-outbox V0002 for the doctrine note.
-- Saga rows convert in lockstep with the outbox; both get committed by the
-- same service deploy.

alter table saga_state alter column created_at      type timestamp using created_at      at time zone 'Asia/Karachi';
alter table saga_state alter column updated_at      type timestamp using updated_at      at time zone 'Asia/Karachi';
alter table saga_state alter column next_attempt_at type timestamp using next_attempt_at at time zone 'Asia/Karachi';
