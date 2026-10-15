-- Bug #7 — local projection of (role, user) memberships, replicated
-- from auth via the existing auth.user.role_assigned / role_revoked
-- events. Used by EventDispatcher to fan out notifications when a
-- workflow stage targets a role and resolves no specific user
-- (today's HR-approval gap).
--
-- Projection table — not a business entity. PK is the natural
-- (role_code, user_id) pair so upsert / delete by event are O(1) and
-- replays are no-ops. No audit cols: source of truth is auth's user
-- table; granted_at carries the wall-clock the upsert landed.

create table role_members (
    role_code   varchar(64) not null,
    user_id     bigint      not null,
    granted_at  timestamp   not null default now(),
    primary key (role_code, user_id)
);

create index idx_role_members_user on role_members (user_id);
