-- §2 employee data ETL: legacy WebHRMS staging.* → employee.* per-employee
-- tables. Depends on §1 (catalogs in id_map).
--
-- Strategy:
--   - Single transaction.
--   - employees: two-pass for manager_id (insert with NULL, then UPDATE).
--   - id_map populated for apis_employeinfo→employees so dependent tables
--     (history/qualifications/certifications/experience/disabilities)
--     resolve by it.
--   - Sub-tables that key by `cardno` (apis_empeducation) resolve via the
--     newly-loaded employee_code.
--
-- Quirks handled:
--   - empstatus is True/False (not Active/Resigned) — True → ACTIVE,
--     False → TERMINATED (with employee_exits row).
--   - employmenttype_id 8/9/10 → PERMANENT/CONTRACT/INTERN.
--   - bloodgroup: only valid values kept; "Not Set"/garbage → NULL.
--   - cnic: dashes stripped, kept first 13 digits.
--   - email: only kept if contains '@'.
--   - 3 employeinfo rows have no empcompanyinfo — they get an INNER JOIN
--     filter and are dropped (no joining_date is unrecoverable).

BEGIN;

-- ---------------------------------------------------------------------------
-- §2.1 pass 1: insert all employees with NULL manager_id, build id_map
-- ---------------------------------------------------------------------------
WITH joined AS (
    SELECT
        ei.id AS source_id,
        -- employee_code: legacy cardno verbatim. Don't zero-pad — devices
        -- transmit cardno as-entered, and any normalization here would
        -- prevent the attendance-poller's (deviceId, cardNumber) lookup
        -- from finding the employee row.
        ei.cardno AS employee_code,
        left(trim(ei.name), 128) AS full_name,
        -- CNIC: strip non-digits, keep first 13
        NULLIF(left(regexp_replace(coalesce(ei.cnicno, ''), '[^0-9]', '', 'g'), 13), '') AS cnic,
        NULLIF(ei.cnicissuancedate, '')::date AS cnic_issue_date,
        -- chk_cnic_dates demands expiry > issue. Drop expiry when legacy
        -- has both equal (data-entry mistake) or expiry <= issue.
        CASE
            WHEN NULLIF(ei.cninexpirydate, '') IS NULL THEN NULL
            WHEN NULLIF(ei.cnicissuancedate, '') IS NULL THEN ei.cninexpirydate::date
            WHEN ei.cninexpirydate::date > ei.cnicissuancedate::date THEN ei.cninexpirydate::date
            ELSE NULL
        END AS cnic_expiry_date,
        NULLIF(left(trim(eci.ntnno), 32), '')        AS ntn,
        NULLIF(left(trim(eci.insuranceno), 64), '')  AS insurance_number,
        NULLIF(left(trim(eci.sscno), 64), '')        AS ssc_number,
        NULLIF(left(trim(eci.eobino), 32), '')       AS eobi_number,
        NULLIF(ei.dateofbirth, '')::date AS date_of_birth,
        CASE upper(trim(ei.gender))
            WHEN 'MALE'   THEN 'MALE'
            WHEN 'FEMALE' THEN 'FEMALE'
            WHEN 'OTHER'  THEN 'OTHER'
            ELSE NULL
        END AS gender,
        CASE upper(trim(ei.maritalstatus))
            WHEN 'SINGLE'   THEN 'SINGLE'
            WHEN 'MARRIED'  THEN 'MARRIED'
            WHEN 'DIVORCED' THEN 'DIVORCED'
            WHEN 'WIDOW'    THEN 'WIDOWED'
            WHEN 'WIDOWED'  THEN 'WIDOWED'
            ELSE NULL
        END AS marital_status,
        NULLIF(left(trim(ei.fathername), 128), '')   AS father_name,
        NULLIF(left(trim(ei.mothername), 128), '')   AS mother_name,
        NULLIF(left(trim(ei.husbandname), 128), '')  AS spouse_name,
        NULLIF(left(trim(ei.religion), 32), '')      AS religion,
        -- Blood group: only canonical values, else NULL
        CASE upper(trim(ei.bloodgroup))
            WHEN 'A+' THEN 'A+'
            WHEN 'A-' THEN 'A-'
            WHEN 'B+' THEN 'B+'
            WHEN 'B-' THEN 'B-'
            WHEN 'AB+' THEN 'AB+'
            WHEN 'AB-' THEN 'AB-'
            WHEN 'O+' THEN 'O+'
            WHEN 'O-' THEN 'O-'
            ELSE NULL
        END AS blood_group,
        CASE WHEN ei.official_email_address LIKE '%@%'
             THEN left(trim(ei.official_email_address), 255)
             ELSE NULL
        END AS work_email,
        NULLIF(left(trim(ei.contactno), 32), '')           AS phone,
        NULLIF(trim(ei.emppermanentaddress), '')           AS permanent_address,
        NULLIF(trim(ei.empcurrentaddress), '')             AS current_address,
        cmap.target_id AS city_id,
        eci.dateofjoning::date AS joining_date,
        NULLIF(eci.dateofpermanent, '')::date AS confirmation_date,
        CASE WHEN eci.empstatus = 'True' THEN 'ACTIVE' ELSE 'TERMINATED' END AS employment_status,
        CASE eci.employmenttype_id
            WHEN '8'  THEN 'PERMANENT'
            WHEN '9'  THEN 'CONTRACT'
            WHEN '10' THEN 'INTERN'
            ELSE 'PERMANENT'
        END AS employment_type,
        dmap.target_id AS department_id,
        smap.target_id AS section_id,
        desmap.target_id AS designation_id,
        gmap.target_id AS grade_id,
        eci.empreportto_id AS legacy_manager_source_id,
        COALESCE(NULLIF(ei.creationdate, '')::timestamp,
                 NULLIF(ei.entrydatetime, '')::timestamp,
                 now()) AS created_at,
        eci.empstatus AS legacy_empstatus,
        eci.leavedate AS legacy_leavedate,
        eci.leavedesc AS legacy_leavedesc,
        eci.leavingnoticedate AS legacy_notice_date,
        ei.disability_id AS legacy_disability_source_id
    FROM staging.apis_employeinfo ei
    JOIN staging.apis_empcompanyinfo eci ON eci.employee_id = ei.id
    LEFT JOIN migration.id_map cmap   ON cmap.source_table   = 'apis_city'        AND cmap.target_table   = 'cities'        AND cmap.source_id   = ei.empcity_id
    LEFT JOIN migration.id_map dmap   ON dmap.source_table   = 'apis_department'  AND dmap.target_table   = 'departments'   AND dmap.source_id   = eci.department_id
    LEFT JOIN migration.id_map smap   ON smap.source_table   = 'apis_section'     AND smap.target_table   = 'sections'      AND smap.source_id   = eci.section_id
    LEFT JOIN migration.id_map desmap ON desmap.source_table = 'apis_designations' AND desmap.target_table = 'designations'  AND desmap.source_id = eci.designation_id
    LEFT JOIN migration.id_map gmap   ON gmap.source_table   = 'apis_addgrade'    AND gmap.target_table   = 'grades'        AND gmap.source_id   = eci.gradeid_id
    WHERE eci.dateofjoning IS NOT NULL AND eci.dateofjoning <> ''
), inserted AS (
    INSERT INTO employee.employees (
        employee_code, full_name, cnic, cnic_issue_date, cnic_expiry_date,
        ntn, insurance_number, ssc_number, eobi_number,
        date_of_birth, gender, marital_status,
        father_name, mother_name, spouse_name, religion, blood_group,
        work_email, phone, permanent_address, current_address,
        city_id, country, joining_date, confirmation_date,
        employment_status, employment_type,
        department_id, section_id, designation_id, grade_id, manager_id,
        created_at, updated_at, created_by, updated_by
    )
    SELECT
        employee_code, full_name, cnic, cnic_issue_date, cnic_expiry_date,
        ntn, insurance_number, ssc_number, eobi_number,
        date_of_birth, gender, marital_status,
        father_name, mother_name, spouse_name, religion, blood_group,
        work_email, phone, permanent_address, current_address,
        city_id, 'PK', joining_date, confirmation_date,
        employment_status, employment_type,
        department_id, section_id, designation_id, grade_id, NULL,
        created_at, created_at, 'legacy-migration', 'legacy-migration'
    FROM joined
    RETURNING id, employee_code
), mapped AS (
    INSERT INTO migration.id_map (source_table, source_id, target_table, target_id)
    SELECT 'apis_employeinfo', j.source_id::text, 'employees', i.id
    FROM inserted i JOIN joined j ON j.employee_code = i.employee_code
    RETURNING 1
)
SELECT count(*) AS employees_loaded FROM mapped;

-- ---------------------------------------------------------------------------
-- §2.1 pass 2: backfill manager_id from id_map
-- ---------------------------------------------------------------------------
-- B5 — exclude self-loops. WebHRMS lets an employee report to themselves
-- in apis_empcompanyinfo (e.g. Jawad Khalid). Mapping it through here
-- without the emap <> mmap guard creates manager_id = id, which the
-- V0009 CHECK now forbids and which breaks the directory's "Reports to"
-- card.
UPDATE employee.employees e
SET manager_id = mmap.target_id,
    updated_at = e.updated_at
FROM staging.apis_empcompanyinfo eci
JOIN migration.id_map emap ON emap.source_table = 'apis_employeinfo' AND emap.target_table = 'employees' AND emap.source_id = eci.employee_id
JOIN migration.id_map mmap ON mmap.source_table = 'apis_employeinfo' AND mmap.target_table = 'employees' AND mmap.source_id = eci.empreportto_id
WHERE e.id = emap.target_id
  AND emap.target_id <> mmap.target_id
  AND eci.empreportto_id IS NOT NULL AND eci.empreportto_id <> '';

-- ---------------------------------------------------------------------------
-- §1.2 follow-up: department.head_id from apis_department.hodname_id
-- §1.3 has no obvious legacy field, leave NULL.
-- ---------------------------------------------------------------------------
UPDATE employee.departments d
SET head_id = mmap.target_id
FROM staging.apis_department sd
JOIN migration.id_map dmap ON dmap.source_table = 'apis_department'  AND dmap.target_table = 'departments' AND dmap.source_id = sd.id
JOIN migration.id_map mmap ON mmap.source_table = 'apis_employeinfo' AND mmap.target_table = 'employees'   AND mmap.source_id = sd.hodname_id
WHERE d.id = dmap.target_id
  AND sd.hodname_id IS NOT NULL AND sd.hodname_id <> '';

-- ---------------------------------------------------------------------------
-- §2.2 employee_history: one current snapshot per employee
-- ---------------------------------------------------------------------------
--echo 'employee_history target schema lookup...'
INSERT INTO employee.employee_history (
    employee_id, effective_from, effective_to,
    department_id, section_id, designation_id, grade_id, manager_id,
    change_reason, notes, created_by, updated_by
)
SELECT
    e.id,
    COALESCE(NULLIF(eci.depteffectivedate, '')::date, e.joining_date),
    NULL,
    e.department_id, e.section_id, e.designation_id, e.grade_id, e.manager_id,
    'ONBOARD',
    'Migrated from WebHRMS — current assignment only; historical chain not migrated.',
    'legacy-migration', 'legacy-migration'
FROM employee.employees e
JOIN migration.id_map emap ON emap.source_table = 'apis_employeinfo' AND emap.target_table = 'employees' AND emap.target_id = e.id
JOIN staging.apis_empcompanyinfo eci ON eci.employee_id = emap.source_id;

-- ---------------------------------------------------------------------------
-- §2.3 employee_exits — intentionally NOT migrated.
-- The TERMINATED status on employees is enough for now; HR can stand up
-- exit records (with proper exit_type, notice dates, clearance flow) for
-- the small subset where it actually matters post-migration. Loading every
-- legacy resignation as a synthetic 'RESIGNATION' exit row added noise
-- without giving HR anything they couldn't reconstruct from
-- employees.employment_status + the legacy leavedate that's already on the
-- staging row (kept indefinitely on the staging schema for reference).
-- ---------------------------------------------------------------------------

-- ---------------------------------------------------------------------------
-- §2.3b employment_contracts — one row per legacy employee. The new-hire
-- onboarding service creates one as PROBATION with probation_days set;
-- legacy already passed probation (confirmation_date is set on most
-- permanent rows), so we synthesize a single non-probation contract:
--   - PERMANENT employees   → contract_type=PERMANENT
--   - CONTRACT/INTERN       → contract_type=FIXED_TERM
--   - TERMINATED employees  → status=TERMINATED, end_date=leavedate
--   - ACTIVE employees      → status=ACTIVE, end_date=null
-- HR can amend the row (or stack new ones) post-migration as needed.
-- ---------------------------------------------------------------------------
INSERT INTO employee.employment_contracts (
    employee_id, contract_type, start_date, end_date,
    status, probation_days, is_extended,
    created_at, updated_at, created_by, updated_by
)
SELECT
    e.id,
    CASE e.employment_type
        WHEN 'PERMANENT' THEN 'PERMANENT'
        ELSE 'FIXED_TERM'
    END,
    e.joining_date,
    CASE
        WHEN e.employment_status = 'TERMINATED'
            THEN NULLIF(eci.leavedate, '')::date
        ELSE NULL
    END,
    CASE
        WHEN e.employment_status = 'TERMINATED' THEN 'TERMINATED'
        ELSE 'ACTIVE'
    END,
    NULL,
    false,
    e.created_at, e.created_at, 'legacy-migration', 'legacy-migration'
FROM employee.employees e
JOIN migration.id_map emap ON emap.source_table = 'apis_employeinfo' AND emap.target_table = 'employees' AND emap.target_id = e.id
JOIN staging.apis_empcompanyinfo eci ON eci.employee_id = emap.source_id;

-- ---------------------------------------------------------------------------
-- §2.4 employee_qualifications from apis_empeducation (resolve via cardno;
-- match qualification by name to existing catalog).
-- ---------------------------------------------------------------------------
INSERT INTO employee.employee_qualifications (
    employee_id, qualification_id, institution, board_or_university,
    year_of_completion, grade, document_key,
    created_by, updated_by
)
SELECT
    e.id,
    q.id,
    'Unknown' AS institution,           -- legacy didn't capture institution
    NULL,                               -- ditto
    NULL,                               -- ditto
    NULL,                               -- ditto
    NULLIF(trim(ed.educationpic), ''),
    'legacy-migration', 'legacy-migration'
FROM staging.apis_empeducation ed
JOIN employee.employees e ON e.employee_code = ed.cardno
JOIN employee.qualifications q ON lower(q.name) = lower(trim(ed.educationtype));

-- ---------------------------------------------------------------------------
-- §2.4b employee_qualifications fallback from apis_empcompanyinfo.qualification.
-- The §2.4 join via apis_empeducation only covers ~150 employees; another
-- ~3.6k have an inline qualification on their company info row only
-- ("Matric", "F.A", "B.Com", "Un-Educated", ...). Match by normalized
-- (alphanumeric-only, lowercase) name vs catalog name OR catalog code.
-- Only inserts for employees who don't already have any qualification row
-- — keeps the apis_empeducation set as authoritative when both exist.
-- "Un-Educated" / blank entries are dropped.
-- ---------------------------------------------------------------------------
WITH legacy AS (
    SELECT ei.id AS source_emp_id,
           trim(eci.qualification) AS qual_name,
           regexp_replace(lower(trim(eci.qualification)), '[^a-z0-9]', '', 'g') AS qual_norm
    FROM staging.apis_empcompanyinfo eci
    JOIN staging.apis_employeinfo ei ON ei.id = eci.employee_id
    WHERE NULLIF(trim(eci.qualification), '') IS NOT NULL
      AND lower(trim(eci.qualification)) NOT IN ('un-educated','uneducated','none','-','n/a','na')
), with_emp AS (
    SELECT emap.target_id AS employee_id, l.qual_name, l.qual_norm
    FROM legacy l
    JOIN migration.id_map emap
      ON emap.source_table = 'apis_employeinfo' AND emap.target_table = 'employees'
     AND emap.source_id = l.source_emp_id
), matched AS (
    SELECT DISTINCT ON (w.employee_id) w.employee_id, q.id AS qualification_id
    FROM with_emp w
    JOIN employee.qualifications q
      ON regexp_replace(lower(q.code), '[^a-z0-9]', '', 'g') = w.qual_norm
      OR regexp_replace(lower(q.name), '[^a-z0-9]', '', 'g') = w.qual_norm
    ORDER BY w.employee_id,
             -- prefer code match over name match
             (regexp_replace(lower(q.code), '[^a-z0-9]', '', 'g') = w.qual_norm) DESC,
             q.id
)
INSERT INTO employee.employee_qualifications (
    employee_id, qualification_id, institution,
    created_by, updated_by
)
SELECT m.employee_id, m.qualification_id, 'Unknown',
       'legacy-migration', 'legacy-migration'
FROM matched m
LEFT JOIN employee.employee_qualifications eq
  ON eq.employee_id = m.employee_id
WHERE eq.id IS NULL;

-- ---------------------------------------------------------------------------
-- §2.5 employee_certifications from apis_certificate (resolve via id_map)
-- ---------------------------------------------------------------------------
INSERT INTO employee.employee_certifications (
    employee_id, name, issuing_body, issue_date, expiry_date,
    credential_id, document_key, created_by, updated_by
)
SELECT
    emap.target_id,
    left(trim(c.course_name), 255),
    NULLIF(left(trim(c.institute_name), 255), ''),
    NULLIF(c.issue_date, '')::date,
    NULL,
    NULLIF(left(trim(c.course_level), 128), ''),
    NULL,
    'legacy-migration', 'legacy-migration'
FROM staging.apis_certificate c
JOIN migration.id_map emap ON emap.source_table = 'apis_employeinfo' AND emap.target_table = 'employees' AND emap.source_id = c.employee_id
WHERE c.course_name IS NOT NULL AND trim(c.course_name) <> '';

-- ---------------------------------------------------------------------------
-- §2.6 employee_prior_experience from apis_experience
-- chk_prior_dates: end_date >= start_date — drop violators.
-- ---------------------------------------------------------------------------
INSERT INTO employee.employee_prior_experience (
    employee_id, employer_name, designation, start_date, end_date,
    responsibilities, reason_for_leaving, created_by, updated_by
)
SELECT
    emap.target_id,
    left(trim(x.company_name), 255),
    NULLIF(left(trim(x.designation), 255), ''),
    x.from_date::date,
    x.to_date::date,
    NULL,
    NULLIF(left(trim(x.leaving_reason), 255), ''),
    'legacy-migration', 'legacy-migration'
FROM staging.apis_experience x
JOIN migration.id_map emap ON emap.source_table = 'apis_employeinfo' AND emap.target_table = 'employees' AND emap.source_id = x.employee_id
WHERE x.company_name IS NOT NULL AND trim(x.company_name) <> ''
  AND x.from_date IS NOT NULL AND x.from_date <> ''
  AND x.to_date IS NOT NULL AND x.to_date <> ''
  AND x.to_date::date >= x.from_date::date;

-- ---------------------------------------------------------------------------
-- §2.7 employee_disabilities — classify legacy free-text descriptions
-- into the seeded ICF categories. uq_employee_disability (employee_id,
-- disability_id) — dedupe in ETL.
-- Severity comes from `apis_employeinfo.disabilitynature` (Mild / Severe);
-- maps to chk_disability_severity ('MILD','MODERATE','SEVERE').
-- ---------------------------------------------------------------------------
WITH classified AS (
    SELECT
        ei.id AS source_emp_id,
        trim(ad.name) AS legacy_desc,
        CASE
            WHEN trim(ad.name) ILIKE '%deaf%dumb%' OR trim(ad.name) ILIKE '%dumb%deaf%' THEN 'MULTIPLE'
            WHEN trim(ad.name) ~* '(deaf|hearing)' THEN 'HEARING'
            WHEN trim(ad.name) ~* '(dumb|speech|mute)' THEN 'SPEECH'
            WHEN trim(ad.name) ~* '(blind|visual|eye|sight)' THEN 'VISUAL'
            WHEN trim(ad.name) ~* '(polio|paralyzed|paralysis|leg|arm|foot|hand|body|short|tall|spine|joint|fracture)' THEN 'PHYSICAL'
            ELSE 'OTHER'
        END AS disability_code,
        CASE upper(trim(coalesce(ei.disabilitynature, '')))
            WHEN 'MILD'   THEN 'MILD'
            WHEN 'SEVERE' THEN 'SEVERE'
            ELSE NULL
        END AS severity
    FROM staging.apis_employeinfo ei
    JOIN staging.apis_adddisability ad ON ad.id = ei.disability_id
    WHERE ei.disability_id IS NOT NULL AND ei.disability_id <> ''
), resolved AS (
    SELECT DISTINCT ON (emap.target_id, d.id)
        emap.target_id AS employee_id,
        d.id           AS disability_id,
        c.legacy_desc  AS description,
        c.severity
    FROM classified c
    JOIN migration.id_map emap ON emap.source_table = 'apis_employeinfo' AND emap.target_table = 'employees' AND emap.source_id = c.source_emp_id
    JOIN employee.disabilities d ON d.code = c.disability_code
)
INSERT INTO employee.employee_disabilities (
    employee_id, disability_id, description, severity, document_key,
    created_by, updated_by
)
SELECT employee_id, disability_id, left(description, 255), severity, NULL,
       'legacy-migration', 'legacy-migration'
FROM resolved;

-- ---------------------------------------------------------------------------
-- §2.8 attendance_employees bridge: re-populate from employee.employees so
-- the attendance service can resolve biometric punches by employee_code.
--
-- V0014__employee_code_snapshot.sql does this on first Flyway run, but
-- Flyway won't re-fire V0014 on subsequent ETL re-runs. Without this step,
-- the bridge keeps the employee_ids from the previous run while
-- employee.employees gets fresh ids — every punch then lands on the wrong
-- employee. Idempotent: ON CONFLICT no-op (00_reset truncates this table
-- so the conflict path only matters for partial re-runs).
-- ---------------------------------------------------------------------------
-- employment_status (V0021) and joining_date (V0022) are both load-bearing
-- downstream: attendance filters rosters/punches on status, and
-- LeaveCycleResolver anchors JOINING-cycle leave types (ANNUAL) on
-- joining_date — a NULL there silently falls back to a calendar-year
-- cycle. Both must be carried over, not left to their defaults.
INSERT INTO attendance.attendance_employees (
    employee_id, employee_code, full_name,
    employment_status, joining_date, created_by, updated_by
)
SELECT id, employee_code, full_name,
       employment_status, joining_date, 'legacy-migration', 'legacy-migration'
FROM employee.employees
ON CONFLICT (employee_id) DO UPDATE
   SET employee_code     = EXCLUDED.employee_code,
       full_name         = EXCLUDED.full_name,
       employment_status = EXCLUDED.employment_status,
       joining_date      = EXCLUDED.joining_date,
       updated_by        = 'legacy-migration';

COMMIT;

-- summary
SELECT 'employees'                 AS target, count(*) FROM employee.employees                 UNION ALL
SELECT 'employees ACTIVE',                    count(*) FROM employee.employees WHERE employment_status = 'ACTIVE' UNION ALL
SELECT 'employees TERMINATED',                count(*) FROM employee.employees WHERE employment_status = 'TERMINATED' UNION ALL
SELECT 'employees with manager',              count(*) FROM employee.employees WHERE manager_id IS NOT NULL UNION ALL
SELECT 'departments with head',               count(*) FROM employee.departments WHERE head_id IS NOT NULL UNION ALL
SELECT 'employee_history',                    count(*) FROM employee.employee_history          UNION ALL
SELECT 'employment_contracts',                count(*) FROM employee.employment_contracts      UNION ALL
SELECT 'employee_qualifications',             count(*) FROM employee.employee_qualifications   UNION ALL
SELECT 'employee_certifications',             count(*) FROM employee.employee_certifications   UNION ALL
SELECT 'employee_prior_experience',           count(*) FROM employee.employee_prior_experience UNION ALL
SELECT 'employee_disabilities',               count(*) FROM employee.employee_disabilities;
