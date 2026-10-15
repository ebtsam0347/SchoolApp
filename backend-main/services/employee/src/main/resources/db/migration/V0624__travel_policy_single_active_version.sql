-- ============================================================
-- Travel Module
-- Rule: AT MOST ONE ACTIVE version per travel policy code.
--
-- A travel policy's version numbers are the history of ONE
-- policy's business rules, not a set of simultaneously
-- applicable policies:
--
--   LOCAL_FACTORY_TRAVEL          v1 RETIRED
--   LOCAL_FACTORY_TRAVEL          v2 RETIRED
--   LOCAL_FACTORY_TRAVEL          v3 ACTIVE   <- the current rules
--
--   INTERNATIONAL_FACTORY_TRAVEL  v1 RETIRED
--   INTERNATIONAL_FACTORY_TRAVEL  v2 ACTIVE
--
-- Travel Request policy selection must therefore find exactly
-- one applicable ACTIVE version. Without this index the service
-- could only *detect* duplicates; "highest version wins" would
-- silently paper over inconsistent master data.
--
-- A plain UNIQUE (policy_code, status) is wrong here: it would
-- also forbid a second DRAFT / INACTIVE / RETIRED row, which are
-- all legitimate. A partial unique index scoped to ACTIVE is the
-- exact rule. DRAFT may coexist with ACTIVE (that is how a
-- replacement version is prepared), and the rule is per
-- policy_code, so LOCAL_FACTORY_TRAVEL and
-- INTERNATIONAL_FACTORY_TRAVEL each keep their own ACTIVE version.
-- ============================================================


-- ------------------------------------------------------------
-- Pre-flight: refuse on pre-existing duplicates rather than let
-- the index build fail with an opaque message.
--
-- Deliberately does NOT retire, delete or otherwise mutate the
-- offending rows: choosing which version stays ACTIVE is a
-- business decision, not a migration's to make.
-- ------------------------------------------------------------
DO
$$
    DECLARE
        conflicts TEXT;
    BEGIN
        SELECT string_agg(
                       format('%s has %s ACTIVE versions (%s)',
                              policy_code, n, version_list),
                       E'\n  ')
        INTO conflicts
        FROM (SELECT policy_code,
                     count(*)                                  AS n,
                     string_agg('v' || version_no, ', '
                                ORDER BY version_no)           AS version_list
              FROM travel_policy_versions
              WHERE status = 'ACTIVE'
              GROUP BY policy_code
              HAVING count(*) > 1) dup;

        IF conflicts IS NOT NULL THEN
            RAISE EXCEPTION USING
                ERRCODE = 'data_exception',
                MESSAGE = 'travel_policy_versions holds more than one ACTIVE version per policy code',
                DETAIL = conflicts,
                HINT = 'Decide which single version stays ACTIVE and move the others to INACTIVE or ' ||
                       'RETIRED (POST /travel/policies/{id}/deactivate or /retire), then re-run this migration.';
        END IF;
    END
$$;


-- ------------------------------------------------------------
-- The rule itself.
-- ------------------------------------------------------------
CREATE UNIQUE INDEX uq_travel_policy_versions_one_active
    ON travel_policy_versions (policy_code)
    WHERE status = 'ACTIVE';


COMMENT ON INDEX uq_travel_policy_versions_one_active IS
    'At most one ACTIVE version per policy_code. DRAFT/INACTIVE/RETIRED rows are unconstrained, so a replacement version can be prepared as DRAFT alongside the live ACTIVE one.';
