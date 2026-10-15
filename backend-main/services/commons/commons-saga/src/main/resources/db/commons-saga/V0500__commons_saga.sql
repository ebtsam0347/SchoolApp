-- Saga state table — one per service schema. Services include this Flyway
-- location alongside their own:
--   spring.flyway.locations: classpath:db/migration,classpath:db/commons-outbox,classpath:db/commons-saga
--
-- Versioned at V0500 (intentionally high) so commons modules can ship
-- migrations without colliding with services' V000x series. Future
-- commons modules slot in at V0501, V0502, …
--
-- Drives forward-only, bounded-retry sagas. See commons-saga README /
-- SagaRunner javadoc for state-machine details.

-- Drop any unmanaged stub from earlier exploration (the original CLAUDE.md
-- said saga_state would land with payroll, but a dev table with a
-- different shape was left behind). Safe: not in flyway_schema_history.
drop table if exists saga_state;

create table if not exists saga_state (
    id              bigserial    primary key,
    saga_name       varchar(64)  not null,
    correlation_key varchar(128) not null,
    status          varchar(24)  not null default 'RUNNING',
    current_step    varchar(64)  not null,
    step_index      integer      not null default 0,
    attempt_count   integer      not null default 0,
    next_attempt_at timestamptz,
    payload         jsonb        not null,
    last_error      varchar(2000),
    created_at      timestamptz  not null default now(),
    updated_at      timestamptz  not null default now(),
    created_by      varchar(64)  not null,
    updated_by      varchar(64)  not null,
    ip_address      inet,
    constraint saga_state_uk unique (saga_name, correlation_key)
);

create index if not exists idx_saga_state_due on saga_state (status, next_attempt_at);
