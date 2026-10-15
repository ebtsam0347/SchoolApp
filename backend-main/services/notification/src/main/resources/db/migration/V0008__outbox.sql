-- Outbox table — inlined here instead of pulling commons-kafka's
-- db/commons-outbox/ location, because that ships V0001 which collides
-- with this service's own V0001__notification_schema.sql. Final shape
-- after commons-outbox V0501/V0502 (plain `timestamp`, not timestamptz —
-- §0.5.B server-time doctrine).

create table if not exists outbox (
    id              bigserial    primary key,
    topic           varchar(128) not null,
    message_key     varchar(256),
    payload         jsonb        not null,
    is_audit        boolean      not null default false,
    correlation_id  varchar(64),
    status          varchar(16)  not null default 'PENDING',
    created_at      timestamp    not null default now(),
    sent_at         timestamp,
    retry_count     integer      not null default 0,
    next_attempt_at timestamp,
    last_error      varchar(1000)
);

create index if not exists idx_outbox_pending on outbox (status, next_attempt_at);
create index if not exists idx_outbox_topic   on outbox (topic, status);
