-- §0.5.B — see auth V0013 for the doctrine note.

alter table conversation_members alter column joined_at type timestamp using joined_at at time zone 'Asia/Karachi';
alter table conversation_reads alter column updated_at type timestamp using updated_at at time zone 'Asia/Karachi';
alter table conversations alter column created_at type timestamp using created_at at time zone 'Asia/Karachi';
alter table conversations alter column last_message_at type timestamp using last_message_at at time zone 'Asia/Karachi';
alter table conversations alter column updated_at type timestamp using updated_at at time zone 'Asia/Karachi';
alter table message_attachments alter column created_at type timestamp using created_at at time zone 'Asia/Karachi';
alter table message_attachments alter column updated_at type timestamp using updated_at at time zone 'Asia/Karachi';
alter table message_deliveries alter column delivered_at type timestamp using delivered_at at time zone 'Asia/Karachi';
alter table messages alter column created_at type timestamp using created_at at time zone 'Asia/Karachi';
alter table messages alter column deleted_at type timestamp using deleted_at at time zone 'Asia/Karachi';
alter table messages alter column edited_at type timestamp using edited_at at time zone 'Asia/Karachi';
alter table messages alter column updated_at type timestamp using updated_at at time zone 'Asia/Karachi';
