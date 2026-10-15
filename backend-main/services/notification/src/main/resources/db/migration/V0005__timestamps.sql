-- §0.5.B — see auth V0013 for the doctrine note.

alter table notification_processed_events alter column processed_at type timestamp using processed_at at time zone 'Asia/Karachi';
alter table notification_templates alter column created_at type timestamp using created_at at time zone 'Asia/Karachi';
alter table notification_templates alter column updated_at type timestamp using updated_at at time zone 'Asia/Karachi';
alter table notifications alter column created_at type timestamp using created_at at time zone 'Asia/Karachi';
alter table notifications alter column read_at type timestamp using read_at at time zone 'Asia/Karachi';
alter table notifications alter column updated_at type timestamp using updated_at at time zone 'Asia/Karachi';
