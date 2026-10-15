-- §7.auth #13/#32 — case-insensitive email uniqueness, drop redundant index.
--
-- Previously: column-level `unique` (creates a btree on email) PLUS a
-- separate non-unique `idx_users_email` — one of the indexes was wasted.
-- Worse, both are case-sensitive, so `Ahmed@x.com` and `ahmed@x.com` would
-- be two distinct rows. Auth-side service code now lowercases on write,
-- and this migration backs that with a DB-level case-insensitive uniqueness
-- guarantee even if a future caller forgets to normalise.

-- 1. Backfill any existing mixed-case rows so the new index can be built
--    without conflicts. Single-tenant fresh deploy means at worst the seeded
--    admin row is touched.
update users set email = lower(email) where email is not null and email <> lower(email);

-- 2. Drop the redundant non-unique index. The case-insensitive unique index
--    we add below covers lookup by lowered value.
drop index if exists idx_users_email;

-- 3. Drop the column-level unique constraint. We replace it with an
--    expression unique index that enforces uniqueness on lower(email)
--    instead of the raw bytes.
alter table users drop constraint if exists users_email_key;

-- 4. Case-insensitive unique index. NULLs are allowed (multiple users
--    without an email). Postgres treats NULLs as distinct in a unique
--    index by default, so this matches the prior column-level semantics.
create unique index uq_users_email_lower on users (lower(email));
