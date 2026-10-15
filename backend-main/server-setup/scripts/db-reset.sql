-- Full schema reset for the FSHRMS database on this host.
-- Drops ALL application schemas (service schemas + the legacy-import
-- staging/migration pair) and recreates the seven service schemas owned by
-- the env role, so Flyway -- which connects as that role -- can rebuild them.
--
-- Run as the postgres superuser:
--     sudo -u postgres psql -v ON_ERROR_STOP=1 -v env=prod -d prod -f ~/db-reset.sql
\set ON_ERROR_STOP on
\pset pager off

\echo '=== before ==='
SELECT n.nspname AS schema,
       pg_get_userbyid(n.nspowner) AS owner,
       count(c.oid) FILTER (WHERE c.relkind = 'r') AS tables
FROM pg_namespace n
LEFT JOIN pg_class c ON c.relnamespace = n.oid
WHERE n.nspname NOT IN ('pg_catalog','information_schema','pg_toast','public')
GROUP BY 1, 2 ORDER BY 1;

BEGIN;

-- Service schemas.
DROP SCHEMA IF EXISTS auth         CASCADE;
DROP SCHEMA IF EXISTS employee     CASCADE;
DROP SCHEMA IF EXISTS attendance   CASCADE;
DROP SCHEMA IF EXISTS payroll      CASCADE;
DROP SCHEMA IF EXISTS notification CASCADE;
DROP SCHEMA IF EXISTS audit        CASCADE;
DROP SCHEMA IF EXISTS chat         CASCADE;

-- Legacy-import working set. Dropped outright, not recreated: the migration
-- tooling builds these when it runs.
DROP SCHEMA IF EXISTS staging      CASCADE;
DROP SCHEMA IF EXISTS migration    CASCADE;

-- AUTHORIZATION :"env" is the fix for the CREATE-privilege gap. These schemas
-- were owned by postgres while the services connect as the env role, which had
-- USAGE and DML but not CREATE -- so Flyway could not have built its history
-- table in an empty schema.
CREATE SCHEMA auth         AUTHORIZATION :"env";
CREATE SCHEMA employee     AUTHORIZATION :"env";
CREATE SCHEMA attendance   AUTHORIZATION :"env";
CREATE SCHEMA payroll      AUTHORIZATION :"env";
CREATE SCHEMA notification AUTHORIZATION :"env";
CREATE SCHEMA audit        AUTHORIZATION :"env";
CREATE SCHEMA chat         AUTHORIZATION :"env";

-- Flyway runs as the env role and V0501__employee_trgm.sql creates the
-- pg_trgm extension. pg_trgm is TRUSTED so no superuser is needed, but
-- creating any extension still requires CREATE on the DATABASE -- which the
-- role does not get from owning a schema. Without this the employee service
-- dies at startup with `permission denied to create extension "pg_trgm"`.
-- Note the extension is created WITH SCHEMA employee, so the DROP above takes
-- it with the schema; it is not a leftover that survives a reset.
GRANT CREATE ON DATABASE :"env" TO :"env";

COMMIT;

\echo '=== after (expect 7 schemas, owner = env role, create = t, 0 tables) ==='
SELECT n.nspname AS schema,
       pg_get_userbyid(n.nspowner) AS owner,
       has_schema_privilege(:'env', n.nspname, 'CREATE') AS role_can_create,
       count(c.oid) FILTER (WHERE c.relkind = 'r') AS tables
FROM pg_namespace n
LEFT JOIN pg_class c ON c.relnamespace = n.oid
WHERE n.nspname NOT IN ('pg_catalog','information_schema','pg_toast','public')
GROUP BY 1, 2, 3 ORDER BY 1;
