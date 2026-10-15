-- §0.5.B — Trust the server, drop tz on the wire/storage. Every JVM and
-- Postgres process runs TZ=Asia/Karachi; columns become plain `timestamp`
-- and the wall-clock value is what we read/write everywhere. The
-- AT TIME ZONE conversion preserves the existing wall-clock value across
-- the column-type change.

alter table outbox alter column created_at      type timestamp using created_at      at time zone 'Asia/Karachi';
alter table outbox alter column sent_at         type timestamp using sent_at         at time zone 'Asia/Karachi';
alter table outbox alter column next_attempt_at type timestamp using next_attempt_at at time zone 'Asia/Karachi';
