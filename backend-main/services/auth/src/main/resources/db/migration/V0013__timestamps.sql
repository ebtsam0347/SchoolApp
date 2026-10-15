-- §0.5.B — convert all timestamptz columns to timestamp without time zone.
-- Wall-clock value preserved across the type change via AT TIME ZONE
-- 'Asia/Karachi'. JVM and Postgres both run TZ=Asia/Karachi, so reads
-- continue to land on the same wall-clock value.

alter table users alter column created_at     type timestamp using created_at     at time zone 'Asia/Karachi';
alter table users alter column updated_at     type timestamp using updated_at     at time zone 'Asia/Karachi';
alter table users alter column last_login_at  type timestamp using last_login_at  at time zone 'Asia/Karachi';
alter table users alter column locked_until   type timestamp using locked_until   at time zone 'Asia/Karachi';

alter table roles alter column created_at type timestamp using created_at at time zone 'Asia/Karachi';
alter table roles alter column updated_at type timestamp using updated_at at time zone 'Asia/Karachi';

alter table permissions alter column created_at type timestamp using created_at at time zone 'Asia/Karachi';
alter table permissions alter column updated_at type timestamp using updated_at at time zone 'Asia/Karachi';

alter table user_roles alter column granted_at type timestamp using granted_at at time zone 'Asia/Karachi';

alter table sessions alter column issued_at     type timestamp using issued_at     at time zone 'Asia/Karachi';
alter table sessions alter column expires_at    type timestamp using expires_at    at time zone 'Asia/Karachi';
alter table sessions alter column last_seen_at  type timestamp using last_seen_at  at time zone 'Asia/Karachi';
alter table sessions alter column revoked_at    type timestamp using revoked_at    at time zone 'Asia/Karachi';
