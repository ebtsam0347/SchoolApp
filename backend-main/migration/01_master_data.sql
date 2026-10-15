-- §1 master data ETL: legacy WebHRMS staging.* → FSHRMS service schemas.
-- Seeds in employee/payroll/attendance V0003 are assumed already restored.
-- Idempotent on the "extends seed" tables (cities/qualifications/fiscal_years)
-- via name-match skip; not idempotent on tables loaded purely from legacy
-- (departments/sections/designations/grades/banks/shifts/holidays/devices) —
-- those expect empty target tables.

BEGIN;

-- ---------------------------------------------------------------------------
-- Short-code helper: produce a sensible HR-readable code from a free-text
-- name.
--   - strip parenthetical content and any leading "N-" numeric prefix;
--   - one word: full word uppercased (varchar(32) accepts up to 32);
--   - multi-word: initials of each word, uppercased
--     ("Quality Assurance" → "QA", "United Bank Limited" → "UBL").
-- Callers append "_<id>" on collision (handled per-table below).
-- ---------------------------------------------------------------------------
CREATE OR REPLACE FUNCTION migration.short_code(input text) RETURNS text AS $$
DECLARE
    cleaned text;
    words   text[];
    n       int;
BEGIN
    cleaned := coalesce(input, '');
    cleaned := regexp_replace(cleaned, '\([^)]*\)', '', 'g');
    cleaned := regexp_replace(cleaned, '^\s*[0-9]+\s*-\s*', '');
    cleaned := trim(cleaned);
    words   := array_remove(regexp_split_to_array(cleaned, '[^A-Za-z0-9]+'), '');
    n       := coalesce(array_length(words, 1), 0);
    IF n = 0 THEN
        RETURN 'X';
    ELSIF n = 1 THEN
        RETURN upper(left(words[1], 32));
    ELSE
        RETURN upper((SELECT string_agg(left(w, 1), '') FROM unnest(words) AS w));
    END IF;
END;
$$ LANGUAGE plpgsql IMMUTABLE;

-- ---------------------------------------------------------------------------
-- §1.1 employee.cities (extend seed)
-- Province classification: Sindh / KP / AJK known cases; default Punjab.
-- New legacy cities get a 3-letter code from the name (or 8-char truncation),
-- and inherit display_order from legacy id so the dropdown order is stable.
-- ---------------------------------------------------------------------------
WITH legacy AS (
    SELECT
        id,
        trim(name) AS name,
        migration.short_code(name) AS gen_code,
        CASE
            WHEN trim(name) IN ('Karachi','Hyderabad') THEN 'SD'
            WHEN trim(name) IN ('Peshawar','Mardan','Mansehra','Nowshera','Kohat','Malakand') THEN 'KP'
            WHEN trim(name) IN ('Muzaffarabad','Jehlum Valley') THEN 'AJK'
            ELSE 'PB'
        END AS province_code
    FROM staging.apis_city
), to_insert AS (
    SELECT l.id, l.name, l.gen_code, p.id AS province_id,
           1000 + l.id::int AS display_order  -- after seed (10..130)
    FROM legacy l
    JOIN employee.provinces p ON p.code = l.province_code
    LEFT JOIN employee.cities c ON lower(c.name) = lower(l.name)
    WHERE c.id IS NULL
), inserted AS (
    INSERT INTO employee.cities (code, name, province_id, display_order, created_by, updated_by)
    SELECT left(gen_code, 32), name, province_id, display_order, 'legacy-migration', 'legacy-migration'
    FROM to_insert
    RETURNING id, name
), new_map AS (
    INSERT INTO migration.id_map (source_table, source_id, target_table, target_id)
    SELECT 'apis_city', l.id::text, 'cities', i.id
    FROM inserted i JOIN legacy l ON lower(l.name) = lower(i.name)
    RETURNING 1
), existing_map AS (
    INSERT INTO migration.id_map (source_table, source_id, target_table, target_id)
    SELECT 'apis_city', l.id::text, 'cities', c.id
    FROM staging.apis_city sl
    JOIN (SELECT id, trim(name) AS name FROM staging.apis_city) l ON l.id = sl.id
    JOIN employee.cities c ON lower(c.name) = lower(l.name)
    RETURNING 1
)
SELECT (SELECT count(*) FROM new_map) AS cities_inserted,
       (SELECT count(*) FROM existing_map) AS cities_already_present;

-- ---------------------------------------------------------------------------
-- §1.2 employee.departments (legacy is source of truth — empty target)
-- Elegant short code: initials of each word ("Quality Assurance" → "QA").
-- Collisions disambiguated by appending source id.
-- ---------------------------------------------------------------------------
WITH legacy AS (
    SELECT
        id,
        trim(name) AS name,
        migration.short_code(name) AS base_code,
        (status = 'True') AS active
    FROM staging.apis_department
), with_dedup AS (
    SELECT l.*,
        CASE
            WHEN count(*) OVER (PARTITION BY l.base_code) > 1
              AND l.id <> min(l.id) OVER (PARTITION BY l.base_code)
                THEN l.base_code || '_' || l.id
            ELSE l.base_code
        END AS gen_code
    FROM legacy l
), inserted AS (
    INSERT INTO employee.departments (code, name, active, created_by, updated_by)
    SELECT gen_code, name, active, 'legacy-migration', 'legacy-migration'
    FROM with_dedup
    RETURNING id, code
), mapped AS (
    INSERT INTO migration.id_map (source_table, source_id, target_table, target_id)
    SELECT 'apis_department', d.id::text, 'departments', i.id
    FROM inserted i JOIN with_dedup d ON d.gen_code = i.code
    RETURNING 1
)
SELECT count(*) AS departments_loaded FROM mapped;

-- ---------------------------------------------------------------------------
-- §1.3 employee.sections — depends on departments.
-- Legacy sectioncode is a coarse bucket (e.g. "Machine Stitch Production"
-- shared across 59 sections), so derive code from the section name. With
-- 304 sections, pure initials collide hard; always append source id to
-- guarantee uniqueness.
-- ---------------------------------------------------------------------------
WITH legacy AS (
    SELECT
        s.id,
        trim(s.name) AS name,
        s.department_id,
        left(migration.short_code(s.name), 24) || '_' || s.id AS gen_code,
        (s.sectionstatus = 'True') AS active
    FROM staging.apis_section s
), resolved AS (
    SELECT d.id, d.name, d.department_id, d.gen_code, d.active, m.target_id AS dept_target_id
    FROM legacy d
    JOIN migration.id_map m
      ON m.source_table = 'apis_department'
     AND m.target_table = 'departments'
     AND m.source_id = d.department_id
), inserted AS (
    INSERT INTO employee.sections (code, name, department_id, active, created_by, updated_by)
    SELECT gen_code, name, dept_target_id, active, 'legacy-migration', 'legacy-migration'
    FROM resolved
    RETURNING id, code
), mapped AS (
    INSERT INTO migration.id_map (source_table, source_id, target_table, target_id)
    SELECT 'apis_section', r.id::text, 'sections', i.id
    FROM inserted i JOIN resolved r ON r.gen_code = i.code
    RETURNING 1
)
SELECT count(*) AS sections_loaded FROM mapped;

-- ---------------------------------------------------------------------------
-- §1.4 employee.designations
-- category: 'Direct' → DIRECT, 'In-Direct' → INDIRECT, else NULL.
-- Short code: initials of each word ("Assistant Manager Accounts" → "AMA").
-- Collisions disambiguated by appending source id (688 rows, common).
-- ---------------------------------------------------------------------------
WITH legacy AS (
    SELECT
        id,
        trim(name) AS title,
        migration.short_code(name) AS base_code,
        CASE upper(trim(designdetail))
            WHEN 'DIRECT' THEN 'DIRECT'
            WHEN 'IN-DIRECT' THEN 'INDIRECT'
            WHEN 'INDIRECT' THEN 'INDIRECT'
            ELSE NULL
        END AS category,
        (designstatus = 'True') AS active
    FROM staging.apis_designations
), with_dedup AS (
    SELECT l.*,
        CASE
            WHEN count(*) OVER (PARTITION BY l.base_code) > 1
                THEN left(l.base_code, 26) || '_' || l.id
            ELSE l.base_code
        END AS gen_code
    FROM legacy l
), inserted AS (
    INSERT INTO employee.designations (code, title, category, active, created_by, updated_by)
    SELECT gen_code, title, category, active, 'legacy-migration', 'legacy-migration'
    FROM with_dedup
    RETURNING id, code
), mapped AS (
    INSERT INTO migration.id_map (source_table, source_id, target_table, target_id)
    SELECT 'apis_designations', d.id::text, 'designations', i.id
    FROM inserted i JOIN with_dedup d ON d.gen_code = i.code
    RETURNING 1
)
SELECT count(*) AS designations_loaded FROM mapped;

-- ---------------------------------------------------------------------------
-- §1.5 employee.grades
-- code = legacy name verbatim (E-1, M-2, A+, ...). varchar(32) accepts +/-.
-- name = same as code at load time (HR can diverge later).
-- description = legacy description (e.g. "GM/Dir/Pres"), NULL when empty.
-- level derived from prefix.
-- display_order seeded from level so default repo sort is bottom-up.
-- ---------------------------------------------------------------------------
WITH legacy AS (
    SELECT
        id,
        trim(name) AS legacy_name,
        trim(name) AS gen_code,
        NULLIF(trim(description), '') AS gen_description,
        CASE
            WHEN trim(name) LIKE 'E-%' OR trim(name) LIKE 'D-%' THEN 5
            WHEN trim(name) LIKE 'M-%' THEN 4
            WHEN trim(name) LIKE 'OG-%' THEN 3
            WHEN trim(name) LIKE 'S-%' THEN 2
            ELSE 1
        END AS level,
        (gradestatus = 'True') AS active
    FROM staging.apis_addgrade
), inserted AS (
    INSERT INTO employee.grades (code, name, level, description, display_order, active, created_by, updated_by)
    SELECT gen_code, gen_code, level, gen_description, level * 10, active, 'legacy-migration', 'legacy-migration'
    FROM legacy
    RETURNING id, code
), mapped AS (
    INSERT INTO migration.id_map (source_table, source_id, target_table, target_id)
    SELECT 'apis_addgrade', l.id::text, 'grades', i.id
    FROM inserted i JOIN legacy l ON l.gen_code = i.code
    RETURNING 1
)
SELECT count(*) AS grades_loaded FROM mapped;

-- ---------------------------------------------------------------------------
-- §1.6 employee.qualifications (extend seed)
-- Insert legacy rows whose name doesn't already match an existing row.
-- Short code (initials); level: 'OTHER' (legacy didn't classify; HR can update).
-- display_order: 1000 + legacy id so it sorts after the seeded rows.
-- ---------------------------------------------------------------------------
WITH legacy AS (
    SELECT
        id,
        trim(name) AS name,
        migration.short_code(name) AS gen_code
    FROM staging.apis_addqualification
), to_insert AS (
    SELECT l.*, 1000 + l.id::int AS display_order
    FROM legacy l
    LEFT JOIN employee.qualifications q
      ON lower(q.name) = lower(l.name) OR q.code = l.gen_code
    WHERE q.id IS NULL
), inserted AS (
    INSERT INTO employee.qualifications (code, name, level, display_order, created_by, updated_by)
    SELECT left(gen_code, 64), name, 'OTHER', display_order, 'legacy-migration', 'legacy-migration'
    FROM to_insert
    RETURNING id, name, code
), new_map AS (
    INSERT INTO migration.id_map (source_table, source_id, target_table, target_id)
    SELECT 'apis_addqualification', l.id::text, 'qualifications', i.id
    FROM inserted i JOIN legacy l ON lower(l.name) = lower(i.name)
    RETURNING 1
), existing_match AS (
    -- Pick exactly one target row per legacy row: prefer name match, fall
    -- back to code match. DISTINCT ON guarantees one target per legacy id.
    SELECT DISTINCT ON (l.id) l.id AS source_id, q.id AS target_id
    FROM legacy l
    JOIN employee.qualifications q
      ON lower(q.name) = lower(l.name) OR q.code = l.gen_code
    ORDER BY l.id, (lower(q.name) = lower(l.name)) DESC, q.id
), existing_map AS (
    INSERT INTO migration.id_map (source_table, source_id, target_table, target_id)
    SELECT 'apis_addqualification', source_id::text, 'qualifications', target_id
    FROM existing_match
    RETURNING 1
)
SELECT (SELECT count(*) FROM new_map)      AS qualifications_inserted,
       (SELECT count(*) FROM existing_map) AS qualifications_matched_existing;

-- ---------------------------------------------------------------------------
-- §1.8 payroll.banks (legacy is source of truth — empty target)
-- Short code from initials ("UNITED BANK LIMITED" → "UBL", "Meezan Bank" → "MB").
-- account_title constant; iban null.
-- ---------------------------------------------------------------------------
WITH legacy AS (
    SELECT
        id,
        trim(name) AS name,
        migration.short_code(name) AS base_code,
        NULLIF(trim(hrmbankaddress), '') AS address,
        NULLIF(trim(hrmbankaccno),    '') AS account_number,
        (hrmbankstatus = 'True') AS active
    FROM staging.apis_bankdetail
), with_dedup AS (
    SELECT l.*,
        CASE
            WHEN count(*) OVER (PARTITION BY l.base_code) > 1
              AND l.id <> min(l.id) OVER (PARTITION BY l.base_code)
                THEN l.base_code || '_' || l.id
            ELSE l.base_code
        END AS gen_code
    FROM legacy l
), inserted AS (
    INSERT INTO payroll.banks (code, name, branch, address, account_title, iban, account_number, active, created_by, updated_by)
    SELECT gen_code, name, NULL, address, 'Forward Sports Pvt Ltd', NULL, account_number, active,
           'legacy-migration', 'legacy-migration'
    FROM with_dedup
    RETURNING id, code
), mapped AS (
    INSERT INTO migration.id_map (source_table, source_id, target_table, target_id)
    SELECT 'apis_bankdetail', d.id::text, 'banks', i.id
    FROM inserted i JOIN with_dedup d ON d.gen_code = i.code
    RETURNING 1
)
SELECT count(*) AS banks_loaded FROM mapped;

-- ---------------------------------------------------------------------------
-- §1.9 attendance.shifts
-- Legacy names start with a numeric prefix ("1- Normal Day (...)"); use
-- that prefix as the SHIFT-NN code AND the display_order so the dropdown
-- preserves the operations-team's preferred ordering. Names without a
-- leading prefix fall back to SHIFT-{id}.
-- ---------------------------------------------------------------------------
WITH legacy AS (
    SELECT
        id,
        left(trim(name), 64) AS name,
        (regexp_match(trim(name), '^([0-9]+)\s*-'))[1]::int AS leading_no,
        shiftst::time  AS start_time,
        shiftet::time  AS end_time,
        NULLIF(breakst, '00:00:00')::time AS break_start_time,
        NULLIF(breaket, '00:00:00')::time AS break_end_time,
        COALESCE(NULLIF(fridayextrabreak, '')::int, 0) AS friday_extra_minutes,
        (shiftstatus = 'True') AS active
    FROM staging.apis_shift
), coded AS (
    SELECT l.*,
        'SHIFT-' || lpad(coalesce(l.leading_no, l.id::int)::text, 2, '0') AS gen_code,
        coalesce(l.leading_no, 100 + l.id::int) AS display_order
    FROM legacy l
), with_dedup AS (
    SELECT c.*,
        CASE
            WHEN count(*) OVER (PARTITION BY c.gen_code) > 1
              AND c.id <> min(c.id) OVER (PARTITION BY c.gen_code)
                THEN c.gen_code || '_' || c.id
            ELSE c.gen_code
        END AS final_code
    FROM coded c
), inserted AS (
    INSERT INTO attendance.shifts (code, name, start_time, end_time,
                                   break_start_time, break_end_time,
                                   grace_minutes, friday_extra_minutes,
                                   display_order,
                                   active, created_by, updated_by)
    SELECT final_code, name, start_time, end_time,
           break_start_time, break_end_time,
           10, friday_extra_minutes,
           display_order,
           active, 'legacy-migration', 'legacy-migration'
    FROM with_dedup
    RETURNING id, code
), mapped AS (
    INSERT INTO migration.id_map (source_table, source_id, target_table, target_id)
    SELECT 'apis_shift', d.id::text, 'shifts', i.id
    FROM inserted i JOIN with_dedup d ON d.final_code = i.code
    RETURNING 1
)
SELECT count(*) AS shifts_loaded FROM mapped;

-- ---------------------------------------------------------------------------
-- §1.10 attendance.holidays
-- type derived from name pattern; UNIQUE (date, type) handled by deduping
-- to first occurrence per (date, type) — legacy sometimes duplicates.
-- ---------------------------------------------------------------------------
WITH legacy AS (
    SELECT
        id,
        trim(name) AS name,
        holidaydate::date AS holiday_date,
        NULLIF(trim(holidayremarks), '') AS description,
        CASE
            WHEN trim(name) ~* '(eid|ramadan|ramzan|iqbal|quaid|pakistan|independence|kashmir|labour|christmas|ashura|muharram|rabi|miraj)'
                THEN 'NATIONAL'
            ELSE 'COMPANY'
        END AS type
    FROM staging.apis_hrm_hr_holidays
    WHERE holidaydate IS NOT NULL AND holidaydate <> ''
), deduped AS (
    SELECT DISTINCT ON (holiday_date, type)
           id, name, holiday_date, description, type
    FROM legacy
    ORDER BY holiday_date, type, id
), inserted AS (
    INSERT INTO attendance.holidays (holiday_date, name, type, description, created_by, updated_by)
    SELECT holiday_date, name, type, description, 'legacy-migration', 'legacy-migration'
    FROM deduped
    RETURNING id, holiday_date, type
), mapped AS (
    INSERT INTO migration.id_map (source_table, source_id, target_table, target_id)
    SELECT 'apis_hrm_hr_holidays', d.id::text, 'holidays', i.id
    FROM inserted i JOIN deduped d ON d.holiday_date = i.holiday_date AND d.type = i.type
    RETURNING 1
)
SELECT count(*) AS holidays_loaded FROM mapped;

-- ---------------------------------------------------------------------------
-- §1.12 attendance.attendance_devices
-- code: derive from IP last octet (`DEV-021`). Stable across renames and
-- short enough to type. Fall back to `DEV-{id}` for the (currently
-- nonexistent) edge case of a device with no IP. Subnet-collision is not
-- a concern today — every device sits on 10.10.107.0/24 — but if a future
-- device shares a last-octet across subnets, the per-id fallback (`DEV-{id}`)
-- still yields a unique code and HR can rename it.
-- ---------------------------------------------------------------------------
WITH legacy AS (
    SELECT
        id,
        trim(name) AS name,
        NULLIF(trim(location), '') AS location,
        NULLIF(trim(deviceip), '') AS device_ip,
        (status = 'True') AS active,
        -- last octet of the IP, padded to 3 digits, when present
        (regexp_match(NULLIF(trim(deviceip), ''), '\.(\d+)$'))[1] AS ip_last_octet
    FROM staging.payroll_payroll_deviceinfo
), coded AS (
    SELECT l.*,
        CASE
            WHEN l.ip_last_octet IS NOT NULL
                THEN 'DEV-' || lpad(l.ip_last_octet, 3, '0')
            ELSE 'DEV-' || lpad(l.id::text, 3, '0')
        END AS gen_code
    FROM legacy l
), with_dedup AS (
    -- Disambiguate the (theoretical) tie on last-octet by appending the
    -- legacy id; first occurrence keeps the bare code.
    SELECT c.*,
        CASE
            WHEN count(*) OVER (PARTITION BY c.gen_code) > 1
              AND c.id <> min(c.id) OVER (PARTITION BY c.gen_code)
                THEN c.gen_code || '_' || c.id
            ELSE c.gen_code
        END AS final_code
    FROM coded c
), inserted AS (
    INSERT INTO attendance.attendance_devices (code, name, location, device_ip, active, created_by, updated_by)
    SELECT final_code, name, location, device_ip::inet, active, 'legacy-migration', 'legacy-migration'
    FROM with_dedup
    RETURNING id, code
), mapped AS (
    INSERT INTO migration.id_map (source_table, source_id, target_table, target_id)
    SELECT 'payroll_payroll_deviceinfo', d.id::text, 'attendance_devices', i.id
    FROM inserted i JOIN with_dedup d ON d.final_code = i.code
    RETURNING 1
)
SELECT count(*) AS devices_loaded FROM mapped;

-- ---------------------------------------------------------------------------
-- §1.13 payroll.fiscal_years (extend seed; legacy "FY 2025-26" already CURRENT)
-- code: "FY 2025-26" → "2025-26" (max 7 chars).
-- status: CURRENT for the row whose end_date >= today (only one); past → CLOSED;
-- future → FUTURE. Guard the CURRENT uniqueness by skipping if seed already has one.
-- ---------------------------------------------------------------------------
WITH legacy AS (
    SELECT
        id,
        replace(trim(name), 'FY ', '') AS gen_code,
        start_date::date AS start_date,
        end_date::date   AS end_date
    FROM staging.apis_financialperiod
    WHERE start_date IS NOT NULL AND start_date <> ''
), to_insert AS (
    SELECT l.*
    FROM legacy l
    LEFT JOIN payroll.fiscal_years fy ON fy.code = l.gen_code
    WHERE fy.code IS NULL
), classified AS (
    SELECT t.gen_code, t.start_date, t.end_date,
        CASE
            WHEN current_date BETWEEN t.start_date AND t.end_date THEN 'CURRENT'
            WHEN t.end_date < current_date THEN 'CLOSED'
            ELSE 'FUTURE'
        END AS desired_status,
        EXISTS (SELECT 1 FROM payroll.fiscal_years WHERE status = 'CURRENT') AS seed_has_current
    FROM to_insert t
)
INSERT INTO payroll.fiscal_years (code, start_date, end_date, status, created_by, updated_by)
SELECT gen_code, start_date, end_date,
       CASE WHEN desired_status = 'CURRENT' AND seed_has_current THEN 'CLOSED'
            ELSE desired_status END,
       'legacy-migration', 'legacy-migration'
FROM classified;

COMMIT;

-- summary
SELECT 'cities'             AS target, count(*) FROM employee.cities             UNION ALL
SELECT 'departments',                  count(*) FROM employee.departments        UNION ALL
SELECT 'sections',                     count(*) FROM employee.sections           UNION ALL
SELECT 'designations',                 count(*) FROM employee.designations       UNION ALL
SELECT 'grades',                       count(*) FROM employee.grades             UNION ALL
SELECT 'qualifications',               count(*) FROM employee.qualifications     UNION ALL
SELECT 'banks',                        count(*) FROM payroll.banks               UNION ALL
SELECT 'shifts',                       count(*) FROM attendance.shifts           UNION ALL
SELECT 'holidays',                     count(*) FROM attendance.holidays         UNION ALL
SELECT 'attendance_devices',           count(*) FROM attendance.attendance_devices UNION ALL
SELECT 'fiscal_years',                 count(*) FROM payroll.fiscal_years        UNION ALL
SELECT 'id_map rows',                  count(*) FROM migration.id_map;
