-- §7.auth — optimistic-lock column on sessions to close the refresh-token
-- rotation race. Two concurrent /auth/refresh calls with the same inbound
-- refresh token both load the row and both rotate refresh_token_id; without
-- a version column the second commit overwrites the first and Redis ends up
-- with two valid refresh tokens for one session. Hibernate uses
-- @Version on Session.java to make the second commit fail with
-- OptimisticLockException; the failing tx rolls back and its afterCommit
-- Redis put never fires.

alter table sessions
    add column version bigint not null default 0;
