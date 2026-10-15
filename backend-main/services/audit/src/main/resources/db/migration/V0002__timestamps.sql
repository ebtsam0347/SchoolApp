-- §0.5.B — see auth V0013 for the doctrine note.

alter table audit_events alter column occurred_at type timestamp using occurred_at at time zone 'Asia/Karachi';
alter table audit_events alter column ingested_at type timestamp using ingested_at at time zone 'Asia/Karachi';
