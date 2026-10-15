-- ============================================================================
-- SUPERSEDED — DO NOT RUN. Use 07_ess_provisioning.py instead.
--
-- Kept only so the mapping decisions below stay reviewable in one place.
-- Three things make this file wrong for the cutover:
--
--   1. Eligibility. It selects on `apis_employeinfo.loginname_id IS NOT
--      NULL`, which is NOT a per-employee login: 2,325 employees share the
--      value 4181, so it is a shared FK. It overlapped the real account
--      list on only 547 of ~2,600 staff and would deny accounts to 2,050
--      people who have a working password today.
--   2. Passwords. It generates random ones. The business decision is that
--      staff keep the password they already use, imported from the legacy
--      export, so cutover does not feel like a reset.
--   3. Runtime. bcrypt cost 12 single-threaded in one SQL statement takes
--      ~12 minutes for 2,500 users; the Python version does the same work
--      across all cores in ~90 seconds.
-- ============================================================================

-- §6 ESS provisioning for legacy employees who had a Django login.
--
-- Direct SQL approach (rather than N sequential POST /grant-ess calls):
--   - One auth.users row per ESS-eligible employee.
--   - Username = employee_code (unique by construction).
--   - Email left NULL (legacy has many shared/group emails violating the
--     UNIQUE constraint; HR adds personal emails post-migration).
--   - Grant EMPLOYEE role to each.
--   - UPDATE employee.user_id link.
--
-- Eligible: ACTIVE employees with legacy loginname_id IS NOT NULL.
-- TERMINATED employees do NOT get ESS (per FSHRMS termination policy).
--
-- PASSWORDS. Every user gets its own random 10-character password, bcrypt
-- cost 12, with must_change_pw = true so it is dead on first login. The
-- plaintext is written to `migration.ess_initial_passwords` for HR to
-- distribute, and that table is the ONLY copy — see §6.3 for the export
-- and the drop. The old shared 'password123' (and the block that reset
-- admin + poller-svc to it) is gone; admin credentials are set by
-- HRMS_AUTH_BOOTSTRAP_ADMIN_PASSWORD in the sealed secret, and this
-- script must never touch them.
--
-- RUNTIME. bcrypt at cost 12 measures ~0.3s per hash, and this hashes every
-- eligible employee in one transaction: budget ~12 minutes per 2,500 users.
-- It is CPU-bound and single-threaded, it holds one transaction open the
-- whole time, and it prints nothing until it commits. Don't mistake it for
-- a hang and don't lower the cost to speed it up.
--
-- The exported CSV is a credential file wherever users will actually log
-- in — pre-launch prod included. Move it off the host and delete it.

-- crypt() / gen_salt() / gen_random_bytes() live in pgcrypto.
CREATE EXTENSION IF NOT EXISTS pgcrypto;

-- ---------------------------------------------------------------------------
-- §6.1 password generator
-- Unambiguous 32-character alphabet: no I/O/0/1, so a printed slip can't be
-- mistyped. 10 chars => ~50 bits, ample for a credential that must be
-- changed at first login. 256 % 32 == 0, so the byte->index mapping is
-- unbiased. VOLATILE is load-bearing: a STABLE function here would let the
-- planner hoist one password onto every row.
-- ---------------------------------------------------------------------------
CREATE OR REPLACE FUNCTION migration.temp_password(len int DEFAULT 10)
RETURNS text LANGUAGE sql VOLATILE AS $fn$
    SELECT string_agg(
               substr('ABCDEFGHJKLMNPQRSTUVWXYZ23456789',
                      1 + (get_byte(b.bytes, g.i) % 32), 1),
               '' ORDER BY g.i)
      FROM (SELECT gen_random_bytes(len) AS bytes) b,
           generate_series(0, len - 1) AS g(i);
$fn$;

-- migration.ess_initial_passwords is created by 000_bootstrap.sql.

BEGIN;

-- ---------------------------------------------------------------------------
-- §6.2 provision
-- ---------------------------------------------------------------------------
WITH eligible AS (
    SELECT
        e.id AS employee_id,
        e.employee_code,
        e.full_name
    FROM staging.apis_employeinfo ei
    JOIN migration.id_map emap
      ON emap.source_table = 'apis_employeinfo' AND emap.target_table = 'employees'
     AND emap.source_id = ei.id
    JOIN employee.employees e ON e.id = emap.target_id
    WHERE ei.loginname_id IS NOT NULL AND ei.loginname_id <> ''
      AND e.employment_status = 'ACTIVE'
      AND e.user_id IS NULL
), generated AS MATERIALIZED (
    -- MATERIALIZED is load-bearing: `generated` is referenced twice below
    -- (the users insert and the password export) and both must see the
    -- SAME plaintext. Inlining would re-run temp_password() per reference
    -- and hand HR passwords that don't match the stored hashes.
    SELECT e.*, migration.temp_password(10) AS plain FROM eligible e
), role_id AS (
    SELECT id FROM auth.roles WHERE code = 'EMPLOYEE'
), inserted AS (
    INSERT INTO auth.users (
        username, full_name, email, password_hash, status,
        must_change_pw, employee_linked,
        created_by, updated_by
    )
    SELECT
        g.employee_code,
        left(g.full_name, 128),
        NULL,
        crypt(g.plain, gen_salt('bf', 12)),
        'ACTIVE',
        true,          -- forced reset at first login
        true,
        'legacy-migration', 'legacy-migration'
    FROM generated g
    RETURNING id, username
), exported AS (
    INSERT INTO migration.ess_initial_passwords (employee_code, full_name, password)
    SELECT g.employee_code, g.full_name, g.plain
    FROM generated g
    ON CONFLICT (employee_code) DO UPDATE
       SET password = EXCLUDED.password, generated_at = now()
    RETURNING 1
), role_grants AS (
    INSERT INTO auth.user_roles (user_id, role_id, granted_by)
    SELECT i.id, r.id, 'legacy-migration'
    FROM inserted i CROSS JOIN role_id r
    RETURNING 1
), employee_link AS (
    UPDATE employee.employees e
    SET user_id = i.id, updated_at = e.updated_at, updated_by = 'legacy-migration'
    FROM inserted i
    WHERE i.username = e.employee_code
    RETURNING e.id
)
SELECT (SELECT count(*) FROM inserted)      AS users_created,
       (SELECT count(*) FROM role_grants)   AS role_grants,
       (SELECT count(*) FROM employee_link) AS employees_linked,
       (SELECT count(*) FROM exported)      AS passwords_exported;

COMMIT;

-- ---------------------------------------------------------------------------
-- §6.3 hand-off  —  DO NOT SKIP
--
-- Export for HR, then destroy the table. From the DB host:
--
--   \copy (SELECT employee_code, full_name, password
--            FROM migration.ess_initial_passwords ORDER BY employee_code)
--     TO '/tmp/ess-initial-passwords.csv' WITH (FORMAT csv, HEADER true)
--
-- Move that CSV off the host over a channel HR actually uses, confirm
-- receipt, then:
--
--   DROP TABLE migration.ess_initial_passwords;
--
-- Leaving it in place keeps 2,500 live credentials in plaintext in the
-- database. The whole `migration` schema is dropped at sign-off anyway,
-- but don't wait for that on prod.
-- ---------------------------------------------------------------------------

SELECT 'auth.users (post-migration)'      AS metric, count(*) FROM auth.users WHERE created_by = 'legacy-migration' UNION ALL
SELECT 'auth.user_roles (post-migration)',          count(*) FROM auth.user_roles WHERE granted_by = 'legacy-migration' UNION ALL
SELECT 'auth.users must_change_pw=true',            count(*) FROM auth.users WHERE must_change_pw = true UNION ALL
SELECT 'passwords pending hand-off',                count(*) FROM migration.ess_initial_passwords UNION ALL
SELECT 'employees with user_id',                    count(*) FROM employee.employees WHERE user_id IS NOT NULL UNION ALL
SELECT 'employees ACTIVE without user_id',          count(*) FROM employee.employees WHERE user_id IS NULL AND employment_status = 'ACTIVE';
