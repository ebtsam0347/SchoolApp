-- §3 payroll-side per-employee ETL: legacy WebHRMS → payroll.* tables.
-- Depends on §2 (employees + id_map) and §1 (banks + salary_components).
--
-- Salary snapshot strategy: legacy `apis_salary_combined` has multiple
-- rows per employee (history). We take exactly one row per employee —
-- the latest by `effectedfromdate` — as the current snapshot.
--
-- IBAN policy: target column is NOT NULL but legacy never captured IBANs.
-- Insert sentinel 'LEGACY_PENDING' so HR sees the row to update; uniqueness
-- on bank_details is per-employee, so the sentinel is fine.

BEGIN;

-- ---------------------------------------------------------------------------
-- §3.0 seed legacy salary-component codes the unpivot below references.
-- payroll/V0003 only ships HRA, CONVEYANCE, MEDICAL, INCOME_TAX, LOAN,
-- ADVANCE, OTHER. Legacy WebHRMS used a wider catalog of allowance and
-- deduction codes; insert them here so the inner JOIN at §3.4 (and §5.2 in
-- the next file) actually matches. Idempotent — re-running is safe.
-- ---------------------------------------------------------------------------
INSERT INTO payroll.salary_components
    (code, name, type, calc_type, default_amount, display_order, active, created_by, updated_by)
VALUES
    ('MEAL',          'Meal Allowance',          'EARNING',   'FLAT', NULL, 40, true, 'legacy-migration', 'legacy-migration'),
    ('TRAVEL',        'Travel Allowance',        'EARNING',   'FLAT', NULL, 41, true, 'legacy-migration', 'legacy-migration'),
    ('EDUCATION',     'Education Allowance',     'EARNING',   'FLAT', NULL, 42, true, 'legacy-migration', 'legacy-migration'),
    ('ACCOMMODATION', 'Accommodation Allowance', 'EARNING',   'FLAT', NULL, 43, true, 'legacy-migration', 'legacy-migration'),
    ('EXTRA',         'Extra Allowance',         'EARNING',   'FLAT', NULL, 44, true, 'legacy-migration', 'legacy-migration'),
    ('SPECIAL',       'Special Allowance',       'EARNING',   'FLAT', NULL, 45, true, 'legacy-migration', 'legacy-migration'),
    ('SENIORITY',     'Seniority Allowance',     'EARNING',   'FLAT', NULL, 46, true, 'legacy-migration', 'legacy-migration'),
    ('REWARD_POLICY', 'Reward Policy',           'EARNING',   'FLAT', NULL, 47, true, 'legacy-migration', 'legacy-migration'),
    ('RENT',          'Rent Deduction',          'DEDUCTION', 'FLAT', NULL, 92, true, 'legacy-migration', 'legacy-migration'),
    ('CANTEEN',       'Canteen Deduction',       'DEDUCTION', 'FLAT', NULL, 93, true, 'legacy-migration', 'legacy-migration'),
    ('UNION_FUND',    'Union Fund',              'DEDUCTION', 'FLAT', NULL, 94, true, 'legacy-migration', 'legacy-migration')
ON CONFLICT (code) DO NOTHING;

-- ---------------------------------------------------------------------------
-- §3.1 employee_payroll_settings — one row per ACTIVE employee.
--
-- ACTIVE-only because PayrollComputeService.computeRun iterates
-- settingsRepo.findAll(); a settings row IS the inclusion signal. The
-- termination flow (employee.terminated Kafka consumer →
-- EmployeePayrollSettingsService.deleteFor) removes the row when an
-- employee is terminated post-onboarding. Loading settings for the 2,989
-- legacy-terminated employees would re-include them in every monthly run
-- and surface as ATTENDANCE_SNAPSHOT_FAILED (they have no current-period
-- attendance). HR can re-add settings manually if a terminated employee
-- needs a final payslip computed.
--
-- EOBI: every employee is enrolled by default. Legacy `eobino` (the
-- statutory EOBI registration number) being blank doesn't mean the
-- employee is opted out — it just means the number wasn't captured in
-- WebHRMS. HR can flip individuals off post-migration if needed.
--
-- PESSI: not represented as a per-employee column today. PESSI is an
-- employer-only cost computed for every employee in PayrollComputeService
-- (see CLAUDE.md). "All employees enrolled" is already true by code path.
--
-- Gratuity start date: default to joining_date when legacy didn't capture
-- one. Most legacy rows have it blank; gratuity accrual starts at hire
-- unless HR has a specific reason otherwise (recognized prior service,
-- contract conversion).
-- ---------------------------------------------------------------------------
INSERT INTO payroll.employee_payroll_settings (
    employee_id, hire_date, eobi_enrolled, payment_mode,
    gratuity_start_date, senior_allowance_start_date,
    created_by, updated_by
)
SELECT
    e.id,
    e.joining_date,
    true AS eobi_enrolled,
    CASE upper(trim(eci.paymenttype))
        WHEN 'TRANSFER' THEN 'BANK_TRANSFER'
        WHEN 'BANK'     THEN 'BANK_TRANSFER'
        WHEN 'CASH'     THEN 'CASH'
        ELSE 'BANK_TRANSFER'
    END AS payment_mode,
    COALESCE(NULLIF(eci.graduitydate, '')::date, e.joining_date) AS gratuity_start_date,
    -- Legacy stored True/False (booleans), not Yes/No. The earlier
    -- 'NO' filter was a no-op so 'False' rows with a stray senallowdate
    -- were leaking through; gate explicitly on True now.
    CASE
        WHEN trim(coalesce(eci.senallowancestatus, '')) <> 'True'
            THEN NULL
        WHEN NULLIF(eci.senallowdate, '') IS NULL
            THEN NULL
        ELSE eci.senallowdate::date
    END AS senior_allowance_start_date,
    'legacy-migration', 'legacy-migration'
FROM employee.employees e
JOIN migration.id_map emap
  ON emap.source_table = 'apis_employeinfo' AND emap.target_table = 'employees'
 AND emap.target_id = e.id
JOIN staging.apis_empcompanyinfo eci ON eci.employee_id = emap.source_id
WHERE e.employment_status = 'ACTIVE';

-- ---------------------------------------------------------------------------
-- §3.2 employee_bank_details — for BANK_TRANSFER employees with a bank_id
-- ---------------------------------------------------------------------------
INSERT INTO payroll.employee_bank_details (
    employee_id, bank_id, branch, account_title, iban, account_number,
    created_by, updated_by
)
SELECT
    e.id,
    bmap.target_id,
    NULL,
    left(e.full_name, 128),
    'LEGACY_PENDING',
    NULLIF(left(trim(eci.bankaccountno), 64), ''),
    'legacy-migration', 'legacy-migration'
FROM employee.employees e
JOIN migration.id_map emap
  ON emap.source_table = 'apis_employeinfo' AND emap.target_table = 'employees'
 AND emap.target_id = e.id
JOIN staging.apis_empcompanyinfo eci ON eci.employee_id = emap.source_id
JOIN migration.id_map bmap
  ON bmap.source_table = 'apis_bankdetail' AND bmap.target_table = 'banks'
 AND bmap.source_id = eci.bank_id
JOIN payroll.employee_payroll_settings eps
  ON eps.employee_id = e.id AND eps.payment_mode = 'BANK_TRANSFER'
WHERE eci.bank_id IS NOT NULL AND eci.bank_id <> '';

-- ---------------------------------------------------------------------------
-- §3.3 employee_salaries — one current row per employee (latest snapshot)
-- ---------------------------------------------------------------------------
WITH latest AS (
    SELECT DISTINCT ON (sc.employee_id)
        sc.employee_id          AS source_emp_id,
        sc.basicsalary,
        sc.effectedfromdate,
        sc.description
    FROM staging.apis_salary_combined sc
    WHERE sc.employee_id IS NOT NULL AND sc.employee_id <> ''
      AND sc.basicsalary IS NOT NULL AND sc.basicsalary <> ''
    -- Activated rows win over merely-latest ones. `apis_salary_combined`
    -- carries pending increments with a future-dated `effectedfromdate` and
    -- `activationstatus='False'`; ordering on the date alone picked those up,
    -- which put 131 employees on a basic legacy never paid — verified against
    -- the Aug-2026 run, where 129 of the 131 had activationstatus='False'.
    -- Not a WHERE filter: 13 employees have only unactivated rows, and a
    -- stale basic beats no salary row at all.
    ORDER BY sc.employee_id,
             (sc.activationstatus = 'True') DESC,
             NULLIF(sc.effectedfromdate, '')::date DESC NULLS LAST
)
INSERT INTO payroll.employee_salaries (
    employee_id, basic, effective_from, effective_to, revision_reason,
    created_by, updated_by
)
SELECT
    emap.target_id,
    (ceil(l.basicsalary::numeric * 100) / 100.0)::numeric(15,2),
    COALESCE(NULLIF(l.effectedfromdate, '')::date, e.joining_date),
    NULL,
    NULLIF(left(trim(l.description), 1024), ''),
    'legacy-migration', 'legacy-migration'
FROM latest l
JOIN migration.id_map emap
  ON emap.source_table = 'apis_employeinfo' AND emap.target_table = 'employees'
 AND emap.source_id = l.source_emp_id
JOIN employee.employees e ON e.id = emap.target_id;

-- ---------------------------------------------------------------------------
-- §3.4 employee_salary_components — unpivot the latest snapshot's
-- non-zero allowance/deduction columns into one row per component code.
-- ---------------------------------------------------------------------------
WITH latest AS (
    SELECT DISTINCT ON (sc.employee_id)
        sc.employee_id AS source_emp_id,
        sc.effectedfromdate,
        sc.mealallowance,
        sc.travelallowance,
        sc.educationallowance,
        sc.medicalallowance,
        sc.accomodationallowance,
        sc.extraallowance,
        sc.specialallowance,
        sc.senallownace,
        sc.emprewardpolicyamount,
        sc.taxdeduction,
        sc.rentdeduction,
        sc.canteendeducts,
        sc.unionfund
    FROM staging.apis_salary_combined sc
    WHERE sc.employee_id IS NOT NULL AND sc.employee_id <> ''
    -- Same activation precedence as §3.3 above — the allowance/deduction
    -- columns live on the same row as `basicsalary`, so picking an
    -- unactivated row here mis-states every component too.
    ORDER BY sc.employee_id,
             (sc.activationstatus = 'True') DESC,
             NULLIF(sc.effectedfromdate, '')::date DESC NULLS LAST
), with_emp AS (
    SELECT emap.target_id AS employee_id,
           COALESCE(NULLIF(l.effectedfromdate, '')::date, e.joining_date) AS effective_from,
           l.*
    FROM latest l
    JOIN migration.id_map emap
      ON emap.source_table = 'apis_employeinfo' AND emap.target_table = 'employees'
     AND emap.source_id = l.source_emp_id
    JOIN employee.employees e ON e.id = emap.target_id
), long AS (
    -- One row per (employee, component, raw_value) by unpivoting the wide
    -- snapshot. VALUES list pairs the legacy column with the catalog code.
    SELECT
        w.employee_id,
        w.effective_from,
        v.code,
        v.value
    FROM with_emp w
    CROSS JOIN LATERAL (VALUES
        ('MEAL',          w.mealallowance),
        ('TRAVEL',        w.travelallowance),
        ('EDUCATION',     w.educationallowance),
        ('MEDICAL',       w.medicalallowance),
        ('ACCOMMODATION', w.accomodationallowance),
        ('EXTRA',         w.extraallowance),
        ('SPECIAL',       w.specialallowance),
        ('SENIORITY',     w.senallownace),
        ('REWARD_POLICY', w.emprewardpolicyamount),
        ('INCOME_TAX',    w.taxdeduction),
        ('RENT',          w.rentdeduction),
        ('CANTEEN',       w.canteendeducts),
        ('UNION_FUND',    w.unionfund)
    ) AS v(code, value)
    WHERE NULLIF(v.value, '') IS NOT NULL
      AND v.value::numeric > 0
)
INSERT INTO payroll.employee_salary_components (
    employee_id, component_id, amount, effective_from, effective_to, notes,
    created_by, updated_by
)
SELECT
    long.employee_id,
    sc.id,
    (ceil(long.value::numeric * 100) / 100.0)::numeric(15,2),
    long.effective_from,
    NULL,
    NULL,
    'legacy-migration', 'legacy-migration'
FROM long
JOIN payroll.salary_components sc ON sc.code = long.code;

COMMIT;

-- summary
SELECT 'employee_payroll_settings' AS target, count(*) FROM payroll.employee_payroll_settings UNION ALL
SELECT 'eobi_enrolled true',                  count(*) FROM payroll.employee_payroll_settings WHERE eobi_enrolled = true UNION ALL
SELECT 'payment_mode BANK_TRANSFER',          count(*) FROM payroll.employee_payroll_settings WHERE payment_mode = 'BANK_TRANSFER' UNION ALL
SELECT 'payment_mode CASH',                   count(*) FROM payroll.employee_payroll_settings WHERE payment_mode = 'CASH' UNION ALL
SELECT 'employee_bank_details',               count(*) FROM payroll.employee_bank_details UNION ALL
SELECT 'employee_salaries (current)',         count(*) FROM payroll.employee_salaries WHERE effective_to IS NULL UNION ALL
SELECT 'employee_salary_components',          count(*) FROM payroll.employee_salary_components UNION ALL
SELECT 'sal_components by code (top)',        NULL::bigint;
SELECT sc.code, count(*) FROM payroll.employee_salary_components esc
  JOIN payroll.salary_components sc ON sc.id = esc.component_id
  GROUP BY 1 ORDER BY 2 DESC;
