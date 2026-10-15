-- §5 payroll transactions: payroll_runs, payslips + lines, permanent_advances
-- and deductions, short_term_advances batches/lines, leave_encashments.
--
-- Skips §5.7 (bonus) per MAPPING.md.
--
-- 60d staging window has only 1 salarymonth (April 2026), 115 payslips.
-- Advances + encashments span more.
--
-- Money convention: legacy stored PKR with .0 decimals; target is bigint
-- paisa with ceil rounding (CLAUDE.md / Money helper).

BEGIN;

-- ---------------------------------------------------------------------------
-- §5.1 payroll_runs — distinct (year, month) from salary_tx
-- ---------------------------------------------------------------------------
WITH legacy AS (
    SELECT DISTINCT ON (date_trunc('month', s.salarymonth::date))
        s.salarymonth::date AS sm,
        s.financialperiod_id,
        NULLIF(s.transactiondate, '')::timestamp AS posted_at
    FROM staging.account_acc_salary_transactions s
    WHERE s.salarymonth IS NOT NULL AND s.salarymonth <> ''
    ORDER BY date_trunc('month', s.salarymonth::date), NULLIF(s.transactiondate, '')::timestamp NULLS LAST
), inserted AS (
    INSERT INTO payroll.payroll_runs (
        fiscal_year_code, period_year, period_month,
        status, attendance_locked, posted_at,
        created_by, updated_by
    )
    SELECT
        replace(fp.name, 'FY ', ''),
        EXTRACT(YEAR FROM l.sm)::int,
        EXTRACT(MONTH FROM l.sm)::int,
        'POSTED',
        true,
        l.posted_at,
        'legacy-migration', 'legacy-migration'
    FROM legacy l
    JOIN staging.apis_financialperiod fp ON fp.id = l.financialperiod_id
    RETURNING id, period_year, period_month
), mapped AS (
    INSERT INTO migration.id_map (source_table, source_id, target_table, target_id)
    SELECT 'payroll_run_period',
           i.period_year || '-' || lpad(i.period_month::text, 2, '0'),
           'payroll_runs', i.id
    FROM inserted i
    RETURNING 1
)
SELECT count(*) AS payroll_runs_loaded FROM mapped;

-- ---------------------------------------------------------------------------
-- §5.2 payslips — one per salary_tx row.
-- ---------------------------------------------------------------------------
WITH stx AS (
    -- Dedup: legacy may have multiple salary rows per employee per month
    -- (corrections, reissues). Pick the latest by transactiondate.
    SELECT DISTINCT ON (e.id, date_trunc('month', s.salarymonth::date))
           s.*,
           e.id AS target_emp_id,
           e.employee_code AS target_emp_code,
           date_trunc('month', s.salarymonth::date)::date AS sm_first
    FROM staging.account_acc_salary_transactions s
    JOIN employee.employees e ON e.employee_code = s.cardno
    WHERE s.salarymonth IS NOT NULL AND s.salarymonth <> ''
    ORDER BY e.id, date_trunc('month', s.salarymonth::date),
             NULLIF(s.transactiondate, '')::timestamp DESC NULLS LAST
), with_run AS (
    SELECT s.*,
           pr.id AS payroll_run_id,
           pr.fiscal_year_code,
           pr.period_year,
           pr.period_month
    FROM stx s
    JOIN payroll.payroll_runs pr
      ON pr.period_year = EXTRACT(YEAR FROM s.sm_first)::int
     AND pr.period_month = EXTRACT(MONTH FROM s.sm_first)::int
), payslip_inserted AS (
    INSERT INTO payroll.payslips (
        payroll_run_id, employee_id, employee_code,
        period_year, period_month, fiscal_year_code,
        basic, allowances, gross, deductions, tax, eobi, pessi_employer, net,
        lop_days, working_days, paid_days, absent_days,
        holidays_in_period, weekends_in_period,
        ot_hours, ot_amount,
        status, held_reason, generated_at,
        created_by, updated_by
    )
    SELECT
        w.payroll_run_id, w.target_emp_id, w.target_emp_code,
        w.period_year, w.period_month, w.fiscal_year_code,
        (ceil(COALESCE(NULLIF(w.basicsalary,'')::numeric, 0)        * 100) / 100.0)::numeric(15,2),
        (ceil(GREATEST(COALESCE(NULLIF(w.totaladds,'')::numeric, 0)
                    - COALESCE(NULLIF(w.arrearsamt,'')::numeric, 0)
                    - COALESCE(NULLIF(w.overtimepaid,'')::numeric, 0), 0) * 100) / 100.0)::numeric(15,2),
        (ceil(COALESCE(NULLIF(w.grosssalary,'')::numeric, 0)        * 100) / 100.0)::numeric(15,2),
        (ceil(GREATEST(COALESCE(NULLIF(w.totalsubtracts,'')::numeric, 0)
                    - COALESCE(NULLIF(w.taxdeductions,'')::numeric, 0)
                    - COALESCE(NULLIF(w.eobi,'')::numeric, 0), 0) * 100) / 100.0)::numeric(15,2),
        (ceil(COALESCE(NULLIF(w.taxdeductions,'')::numeric, 0)      * 100) / 100.0)::numeric(15,2),
        (ceil(COALESCE(NULLIF(w.eobi,'')::numeric, 0)               * 100) / 100.0)::numeric(15,2),
        (ceil(COALESCE(NULLIF(w.pessicontribution,'')::numeric, 0)  * 100) / 100.0)::numeric(15,2),
        (ceil(COALESCE(NULLIF(w.payablesalary,'')::numeric, 0)      * 100) / 100.0)::numeric(15,2),
        COALESCE(NULLIF(w.unpaidleaves,'')::numeric, 0),
        COALESCE(NULLIF(w.workingdaysinmonth,'')::numeric, 0)::int,
        COALESCE(NULLIF(w.totalpayabledays,'')::numeric, 0),
        COALESCE(NULLIF(w.totalabsentees,'')::numeric, 0),
        COALESCE(NULLIF(w.holidaysinmonth,'')::numeric, 0)::int,
        COALESCE(NULLIF(w.sundaysinmonth,'')::numeric, 0)::int,
        COALESCE(NULLIF(w.overtimehours,'')::numeric, 0),
        (ceil(COALESCE(NULLIF(w.overtimepaid,'')::numeric, 0)       * 100) / 100.0)::numeric(15,2),
        -- Legacy stored True/False in paidunpaidstatus (everything we've
        -- seen is 'False'); 'True' → PAID, anything else → HELD.
        CASE WHEN w.paidunpaidstatus = 'True' THEN 'PAID' ELSE 'HELD' END,
        CASE WHEN w.paidunpaidstatus = 'True' THEN NULL ELSE 'migrated as unpaid from legacy' END,
        NULLIF(w.transactiondate, '')::timestamp,
        'legacy-migration', 'legacy-migration'
    FROM with_run w
    RETURNING id, payroll_run_id, employee_id
), mapped AS (
    INSERT INTO migration.id_map (source_table, source_id, target_table, target_id)
    SELECT 'account_acc_salary_transactions', s.id::text, 'payslips', p.id
    FROM payslip_inserted p
    JOIN with_run s ON s.payroll_run_id = p.payroll_run_id AND s.target_emp_id = p.employee_id
    RETURNING 1
)
SELECT count(*) AS payslips_loaded FROM mapped;

-- ---------------------------------------------------------------------------
-- §5.2 payslip_lines — unpivot the wide salary_tx into one line per
-- non-zero allowance/deduction column.
-- ---------------------------------------------------------------------------
WITH stx AS (
    SELECT s.id AS source_tx_id, sc_lookup.target_id AS payslip_id,
           s.mealallowance, s.travelallowance, s.educationallowance,
           s.medicalallow, s.accomallow, s.extraallowance, s.specialallowance,
           s.seniorityallowance, s.taxdeductions, s.rentdeductions,
           s.unionfund, s.eobi, s.overtimepaid, s.arrearsamt
    FROM staging.account_acc_salary_transactions s
    JOIN migration.id_map sc_lookup
      ON sc_lookup.source_table = 'account_acc_salary_transactions'
     AND sc_lookup.target_table = 'payslips'
     AND sc_lookup.source_id = s.id::text
), long AS (
    SELECT s.payslip_id, v.code, v.value, v.display_order
    FROM stx s
    CROSS JOIN LATERAL (VALUES
        ('MEAL',          s.mealallowance,      20),
        ('TRAVEL',        s.travelallowance,    21),
        ('EDUCATION',     s.educationallowance, 22),
        ('MEDICAL',       s.medicalallow,       23),
        ('ACCOMMODATION', s.accomallow,         24),
        ('EXTRA',         s.extraallowance,     25),
        ('SPECIAL',       s.specialallowance,   26),
        ('SENIORITY',     s.seniorityallowance, 27),
        ('INCOME_TAX',    s.taxdeductions,     100),
        ('RENT',          s.rentdeductions,    101),
        ('UNION_FUND',    s.unionfund,         102),
        ('OTHER',         s.eobi,              103)  -- EOBI displayed as a line; reusing OTHER component as catch-all not ideal but FK-valid
    ) AS v(code, value, display_order)
    WHERE NULLIF(v.value, '') IS NOT NULL
      AND v.value::numeric > 0
)
INSERT INTO payroll.payslip_lines (
    payslip_id, component_id, code, name, type, calc_type,
    rate, days, amount, display_order, created_by, updated_by
)
SELECT
    long.payslip_id,
    sc.id,
    sc.code,
    left(sc.name, 64),
    sc.type,
    'FLAT',
    NULL, NULL,
    (ceil(long.value::numeric * 100) / 100.0)::numeric(15,2),
    long.display_order,
    'legacy-migration', 'legacy-migration'
FROM long
JOIN payroll.salary_components sc ON sc.code = long.code;

-- ---------------------------------------------------------------------------
-- §5.3 permanent_advances
-- ---------------------------------------------------------------------------
WITH legacy AS (
    SELECT
        a.id AS source_id,
        e.id AS employee_id,
        (ceil(COALESCE(NULLIF(a.advamt, '')::numeric, 0) * 100) / 100.0)::numeric(15,2) AS amount,
        (ceil(COALESCE(NULLIF(a.dedrate, '')::numeric, 0) * 100) / 100.0)::numeric(15,2) AS monthly_deduction,
        (ceil(COALESCE(NULLIF(a.dedagainstbonus, '')::numeric, 0) * 100) / 100.0)::numeric(15,2) AS bonus_offset,
        (ceil(COALESCE(NULLIF(a.dedagainstaleaves, '')::numeric, 0) * 100) / 100.0)::numeric(15,2) AS annual_leave_offset,
        (ceil(COALESCE(NULLIF(a.graduity, '')::numeric, 0) * 100) / 100.0)::numeric(15,2) AS gratuity_at_request,
        NULLIF(a.receivingdate, '')::date AS receiving_date,
        NULLIF(left(trim(a.description), 4096), '') AS description,
        CASE
            WHEN a.appovalstatus = 'False' OR a.accountsstatus = 'False' OR a.hrstatus = 'False'
                THEN 'REJECTED'
            WHEN a.appovalstatus = 'True' AND a.accountsstatus = 'True' AND a.hrstatus = 'True'
                 AND NULLIF(a.receivingdate, '') IS NOT NULL
                THEN 'DISBURSED'
            ELSE 'PENDING'
        END AS status,
        NULLIF(a.hrstatusdate, '')::timestamp AS hr_decided_at,
        NULLIF(a.accountsstatusdate, '')::timestamp AS accounts_decided_at,
        NULLIF(left(trim(a.appovalremarks), 4096), '') AS hr_note,
        NULLIF(left(trim(a.rejectdescription), 4096), '') AS rejection_reason
    FROM staging.account_acc_permanent_advances a
    JOIN migration.id_map emap
      ON emap.source_table = 'apis_employeinfo' AND emap.target_table = 'employees'
     AND emap.source_id = a.employee_id
    JOIN employee.employees e ON e.id = emap.target_id
    WHERE COALESCE(NULLIF(a.advamt, '')::numeric, 0)  > 0  -- chk_perm_adv_amount
      AND COALESCE(NULLIF(a.dedrate, '')::numeric, 0) > 0  -- chk_perm_adv_monthly
), inserted AS (
    -- V0043 added fiscal_year_code NOT NULL + FK to fiscal_years(code).
    -- Resolve from receiving_date; rows with no receiving date (still
    -- pending in legacy) fall back to the CURRENT fiscal year.
    -- V0041 dropped hr_decided_at / accounts_decided_at / hr_note — that
    -- decision history now lives on approval_steps (written below).
    INSERT INTO payroll.permanent_advances (
        request_code, employee_id, amount, monthly_deduction,
        bonus_offset, annual_leave_offset, gratuity_at_request,
        receiving_date, description, status, fiscal_year_code,
        rejection_reason, created_by, updated_by
    )
    SELECT
        'PA-' || lpad(l.source_id::text, 8, '0'),
        l.employee_id, l.amount, l.monthly_deduction,
        l.bonus_offset, l.annual_leave_offset, l.gratuity_at_request,
        l.receiving_date, l.description, l.status,
        COALESCE(fy.code, cur.code),
        l.rejection_reason,
        'legacy-migration', 'legacy-migration'
    FROM legacy l
    CROSS JOIN (SELECT code FROM payroll.fiscal_years WHERE status = 'CURRENT') cur
    LEFT JOIN payroll.fiscal_years fy
      ON l.receiving_date BETWEEN fy.start_date AND fy.end_date
    RETURNING id, request_code
), mapped AS (
    INSERT INTO migration.id_map (source_table, source_id, target_table, target_id)
    SELECT 'account_acc_permanent_advances', l.source_id::text, 'permanent_advances', i.id
    FROM inserted i
    JOIN legacy l ON i.request_code = 'PA-' || lpad(l.source_id::text, 8, '0')
    RETURNING 1
)
SELECT count(*) AS perm_advances_loaded FROM mapped;

-- §5.3b permanent_advance approval_steps.
-- V0041 moved advance approvals onto the commons-approval engine and
-- dropped the hand-rolled decision columns. Legacy HR/Accounts decisions
-- are replayed as the 2-stage PERMANENT_ADVANCE chain using the same
-- status mapping V0041's own backfill used, so migrated advances show
-- their history in the approvals UI instead of an empty step list.
WITH src AS (
    SELECT
        amap.target_id AS request_id,
        pa.status,
        NULLIF(a.hrstatusdate, '')::timestamp        AS hr_decided_at,
        NULLIF(a.accountsstatusdate, '')::timestamp  AS accounts_decided_at,
        NULLIF(left(trim(a.appovalremarks), 4096), '') AS hr_note,
        pa.created_at
    FROM staging.account_acc_permanent_advances a
    JOIN migration.id_map amap
      ON amap.source_table = 'account_acc_permanent_advances'
     AND amap.target_table = 'permanent_advances'
     AND amap.source_id = a.id
    JOIN payroll.permanent_advances pa ON pa.id = amap.target_id
)
INSERT INTO payroll.approval_steps (
    request_type, request_id, stage_no, resolver_kind, approve_permission, label,
    assigned_to, status, decided_by, decided_at, note,
    created_at, updated_at, created_by, updated_by
)
SELECT 'PERMANENT_ADVANCE', request_id, 1, 'PERMISSION', 'payroll.advance.hr', 'HR Approval',
       NULL::bigint,
       CASE
           WHEN status = 'REJECTED' AND accounts_decided_at IS NULL THEN 'REJECTED'
           WHEN status IN ('HR_APPROVED','APPROVED','DISBURSED','CLOSED')
                OR hr_decided_at IS NOT NULL THEN 'APPROVED'
           WHEN status = 'CANCELLED' THEN 'SKIPPED'
           ELSE 'PENDING'
       END,
       NULL::bigint, hr_decided_at, hr_note,
       created_at, COALESCE(hr_decided_at, created_at),
       'legacy-migration', 'legacy-migration'
FROM src
UNION ALL
SELECT 'PERMANENT_ADVANCE', request_id, 2, 'PERMISSION', 'payroll.advance.accounts', 'Accounts Approval',
       NULL::bigint,
       CASE
           WHEN status = 'REJECTED' AND accounts_decided_at IS NOT NULL THEN 'REJECTED'
           WHEN status IN ('APPROVED','DISBURSED','CLOSED')
                OR accounts_decided_at IS NOT NULL THEN 'APPROVED'
           WHEN (status = 'REJECTED' AND accounts_decided_at IS NULL)
                OR status = 'CANCELLED' THEN 'SKIPPED'
           WHEN status = 'HR_APPROVED' THEN 'PENDING'
           ELSE 'NOT_YET'
       END,
       NULL::bigint, accounts_decided_at, NULL,
       created_at, COALESCE(accounts_decided_at, created_at),
       'legacy-migration', 'legacy-migration'
FROM src;

-- ---------------------------------------------------------------------------
-- §5.4 permanent_advance_deductions
-- Legacy `permanentadvanceid_id` is empty on every row in this table —
-- deductions are linked to the EMPLOYEE, not the parent advance. Resolve
-- by employee_id → the employee's earliest permanent_advance row. Most
-- employees with deductions have a single advance (337 of 340), so the
-- earliest is unambiguous; the 3 employees with 2 advances get all
-- deductions attributed to the older one (HR can split if needed).
-- Deductions for employees who have no permanent_advances row are
-- dropped — there's nothing to attach them to.
-- ---------------------------------------------------------------------------
WITH advance_per_emp AS (
    SELECT DISTINCT ON (a.employee_id)
        a.employee_id  AS source_emp_id,
        amap.target_id AS advance_id,
        pa.employee_id AS employee_id      -- V0047: NOT NULL on the deduction row
    FROM staging.account_acc_permanent_advances a
    JOIN migration.id_map amap
      ON amap.source_table = 'account_acc_permanent_advances'
     AND amap.target_table = 'permanent_advances'
     AND amap.source_id = a.id
    JOIN payroll.permanent_advances pa ON pa.id = amap.target_id
    WHERE NULLIF(a.employee_id, '') IS NOT NULL
    ORDER BY a.employee_id, NULLIF(a.receivingdate, '')::date NULLS LAST, a.id
)
INSERT INTO payroll.permanent_advance_deductions (
    advance_id, employee_id, payroll_run_id, deducted_amount,
    bonus_deduction, annual_leave_deduction, remaining_balance,
    transaction_date, description, created_by, updated_by
)
SELECT
    ape.advance_id,
    ape.employee_id,
    pr.id,  -- match by period; NULL if no run yet for that period
    (ceil(COALESCE(NULLIF(d.padvdedamount, '')::numeric, 0) * 100) / 100.0)::numeric(15,2),
    (ceil(COALESCE(NULLIF(d.bonusdeduction, '')::numeric, 0) * 100) / 100.0)::numeric(15,2),
    (ceil(COALESCE(NULLIF(d.annualleavededuction, '')::numeric, 0) * 100) / 100.0)::numeric(15,2),
    GREATEST(0, (ceil(COALESCE(NULLIF(d.ltaremaining, '')::numeric, 0) * 100) / 100.0)::numeric(15,2)),
    NULLIF(d.transactiondate, '')::date,
    NULLIF(left(trim(d.description), 4096), ''),
    'legacy-migration', 'legacy-migration'
FROM staging.account_acc_permanent_advance_deductions d
JOIN advance_per_emp ape ON ape.source_emp_id = d.employee_id
LEFT JOIN payroll.payroll_runs pr
  ON pr.period_year  = EXTRACT(YEAR  FROM NULLIF(d.transactiondate, '')::date)::int
 AND pr.period_month = EXTRACT(MONTH FROM NULLIF(d.transactiondate, '')::date)::int
WHERE COALESCE(NULLIF(d.padvdedamount, '')::numeric, 0) > 0  -- chk_perm_adv_ded_amount
  AND NULLIF(d.transactiondate, '') IS NOT NULL;

-- ---------------------------------------------------------------------------
-- §5.5 short_term_advance_batches + short_term_advances
-- ---------------------------------------------------------------------------
WITH batch_legacy AS (
    SELECT
        h.id AS source_id,
        dmap.target_id AS department_id,
        smap.target_id AS section_id,
        COALESCE(NULLIF(h.yearadv, '')::numeric::int,
                 EXTRACT(YEAR  FROM NULLIF(h.entrydatetime, '')::timestamp)::int) AS target_year,
        COALESCE(NULLIF(h.monthadv, '')::numeric::int,
                 EXTRACT(MONTH FROM NULLIF(h.entrydatetime, '')::timestamp)::int) AS target_month,
        (ceil(COALESCE(NULLIF(h.totalamount, '')::numeric, 0) * 100) / 100.0)::numeric(15,2) AS total_amount,
        COALESCE(NULLIF(h.totalemployees, '')::numeric::int, 0) AS total_employees,
        CASE
            WHEN h.appovalstatus = 'False' OR h.accountsstatus = 'False' OR h.hrstatus = 'False'
                THEN 'REJECTED'
            WHEN h.appovalstatus = 'True' AND h.accountsstatus = 'True' AND h.hrstatus = 'True'
                THEN 'DISBURSED'
            ELSE 'PENDING'
        END AS status,
        NULLIF(h.hrstatusdate, '')::timestamp AS hr_decided_at,
        NULLIF(h.accountsstatusdate, '')::timestamp AS accounts_decided_at,
        NULLIF(left(trim(h.appovalremarks), 4096), '') AS hr_note,
        NULLIF(left(trim(h.rejectdescription), 4096), '') AS rejection_reason
    FROM staging.account_acc_short_term_advances_head h
    LEFT JOIN migration.id_map dmap ON dmap.source_table='apis_department' AND dmap.target_table='departments' AND dmap.source_id = h.department_id
    LEFT JOIN migration.id_map smap ON smap.source_table='apis_section'    AND smap.target_table='sections'    AND smap.source_id = h.section_id
    WHERE COALESCE(NULLIF(h.yearadv, '')::numeric::int,
                   EXTRACT(YEAR  FROM NULLIF(h.entrydatetime, '')::timestamp)::int) IS NOT NULL
      AND COALESCE(NULLIF(h.monthadv, '')::numeric::int,
                   EXTRACT(MONTH FROM NULLIF(h.entrydatetime, '')::timestamp)::int) BETWEEN 1 AND 12
), batch_inserted AS (
    -- V0041 dropped hr_decided_at / accounts_decided_at / hr_note here too;
    -- the decision history is replayed onto approval_steps below.
    INSERT INTO payroll.short_term_advance_batches (
        request_code, department_id, section_id,
        target_year, target_month, total_amount, total_employees,
        status, rejection_reason,
        created_by, updated_by
    )
    SELECT
        'STA-' || lpad(source_id::text, 8, '0'),
        department_id, section_id, target_year, target_month,
        total_amount, total_employees, status, rejection_reason,
        'legacy-migration', 'legacy-migration'
    FROM batch_legacy
    RETURNING id, request_code
), batch_mapped AS (
    INSERT INTO migration.id_map (source_table, source_id, target_table, target_id)
    SELECT 'account_acc_short_term_advances_head', l.source_id::text, 'short_term_advance_batches', i.id
    FROM batch_inserted i
    JOIN batch_legacy l ON i.request_code = 'STA-' || lpad(l.source_id::text, 8, '0')
    RETURNING 1
)
SELECT count(*) AS sta_batches_loaded FROM batch_mapped;

-- §5.5b short_term_advance_batch approval_steps (same rationale as §5.3b).
WITH src AS (
    SELECT
        bmap.target_id AS request_id,
        b.status,
        NULLIF(h.hrstatusdate, '')::timestamp        AS hr_decided_at,
        NULLIF(h.accountsstatusdate, '')::timestamp  AS accounts_decided_at,
        NULLIF(left(trim(h.appovalremarks), 4096), '') AS hr_note,
        b.created_at
    FROM staging.account_acc_short_term_advances_head h
    JOIN migration.id_map bmap
      ON bmap.source_table = 'account_acc_short_term_advances_head'
     AND bmap.target_table = 'short_term_advance_batches'
     AND bmap.source_id = h.id
    JOIN payroll.short_term_advance_batches b ON b.id = bmap.target_id
)
INSERT INTO payroll.approval_steps (
    request_type, request_id, stage_no, resolver_kind, approve_permission, label,
    assigned_to, status, decided_by, decided_at, note,
    created_at, updated_at, created_by, updated_by
)
SELECT 'SHORT_TERM_ADVANCE', request_id, 1, 'PERMISSION', 'payroll.advance.hr', 'HR Approval',
       NULL::bigint,
       CASE
           WHEN status = 'REJECTED' AND accounts_decided_at IS NULL THEN 'REJECTED'
           WHEN status IN ('HR_APPROVED','APPROVED','DISBURSED','CLOSED')
                OR hr_decided_at IS NOT NULL THEN 'APPROVED'
           WHEN status = 'CANCELLED' THEN 'SKIPPED'
           ELSE 'PENDING'
       END,
       NULL::bigint, hr_decided_at, hr_note,
       created_at, COALESCE(hr_decided_at, created_at),
       'legacy-migration', 'legacy-migration'
FROM src
UNION ALL
SELECT 'SHORT_TERM_ADVANCE', request_id, 2, 'PERMISSION', 'payroll.advance.accounts', 'Accounts Approval',
       NULL::bigint,
       CASE
           WHEN status = 'REJECTED' AND accounts_decided_at IS NOT NULL THEN 'REJECTED'
           WHEN status IN ('APPROVED','DISBURSED','CLOSED')
                OR accounts_decided_at IS NOT NULL THEN 'APPROVED'
           WHEN (status = 'REJECTED' AND accounts_decided_at IS NULL)
                OR status = 'CANCELLED' THEN 'SKIPPED'
           WHEN status = 'HR_APPROVED' THEN 'PENDING'
           ELSE 'NOT_YET'
       END,
       NULL::bigint, accounts_decided_at, NULL,
       created_at, COALESCE(accounts_decided_at, created_at),
       'legacy-migration', 'legacy-migration'
FROM src;

-- short_term_advances detail rows
INSERT INTO payroll.short_term_advances (
    batch_id, employee_id, amount, target_year, target_month,
    status, rejection_reason, created_by, updated_by
)
SELECT
    bmap.target_id,
    e.id,
    (ceil(COALESCE(NULLIF(d.amount, '')::numeric, 0) * 100) / 100.0)::numeric(15,2),
    COALESCE(NULLIF(d.yearadv, '')::numeric::int,
             EXTRACT(YEAR  FROM NULLIF(d.entrydatetime, '')::timestamp)::int),
    COALESCE(NULLIF(d.monthadv, '')::numeric::int,
             EXTRACT(MONTH FROM NULLIF(d.entrydatetime, '')::timestamp)::int),
    CASE
        WHEN d.appovalstatus = 'False' OR d.accountsstatus = 'False' OR d.hrstatus = 'False'
            THEN 'REJECTED'
        WHEN d.appovalstatus = 'True' AND d.accountsstatus = 'True' AND d.hrstatus = 'True'
            THEN 'APPROVED'
        ELSE 'PENDING'
    END,
    NULLIF(left(trim(d.rejectdescription), 4096), ''),
    'legacy-migration', 'legacy-migration'
FROM staging.account_acc_short_term_advances d
JOIN migration.id_map bmap
  ON bmap.source_table = 'account_acc_short_term_advances_head'
 AND bmap.target_table = 'short_term_advance_batches'
 AND bmap.source_id = d.headid_id
JOIN migration.id_map emap2
  ON emap2.source_table = 'apis_employeinfo' AND emap2.target_table = 'employees'
 AND emap2.source_id = d.employee_id
JOIN employee.employees e ON e.id = emap2.target_id
WHERE COALESCE(NULLIF(d.amount, '')::numeric, 0) > 0;  -- chk_sta_amount

-- ---------------------------------------------------------------------------
-- §5.6 leave_encashments
-- chk_le_days requires days_encashed > 0 — filter zero-day rows.
-- uq_le_employee_period (employee_id, calculation_month) — dedupe on first.
-- ---------------------------------------------------------------------------
WITH ranked AS (
    SELECT DISTINCT ON (e.id, NULLIF(le.calculationmonth, '')::date)
        e.id AS employee_id,
        NULLIF(le.calculationmonth, '')::date AS calculation_month,
        -- legacy periodinmonths is 0 for all 11706 rows; default to 12.
        GREATEST(COALESCE(NULLIF(le.periodinmonths, '')::numeric::int, 12), 12) AS period_in_months,
        COALESCE(NULLIF(le.paydays, '')::numeric, 0) AS days_encashed,
        (ceil(COALESCE(NULLIF(le.basicsalary, '')::numeric, 0) * 100) / 100.0)::numeric(15,2) AS basic_salary,
        (ceil(COALESCE(NULLIF(le.dueamount, '')::numeric, 0) * 100) / 100.0)::numeric(15,2) AS amount,
        CASE WHEN le.havepaidornot = 'True' THEN 'PAID' ELSE 'PENDING' END AS payment_status,
        NULLIF(le.transactiondate, '')::timestamp AS paid_at,
        NULLIF(left(trim(le.description), 4096), '') AS description
    FROM staging.account_account_annualleavetransactions le
    JOIN migration.id_map lemap
      ON lemap.source_table = 'apis_employeinfo' AND lemap.target_table = 'employees'
     AND lemap.source_id = le.employee_id
    JOIN employee.employees e ON e.id = lemap.target_id
    WHERE NULLIF(le.calculationmonth, '') IS NOT NULL
      AND COALESCE(NULLIF(le.paydays, '')::numeric, 0) > 0  -- chk_le_days
      -- chk_le_period satisfied by GREATEST(_, 12) above
    ORDER BY e.id, NULLIF(le.calculationmonth, '')::date,
             NULLIF(le.transactiondate, '')::timestamp DESC NULLS LAST
)
INSERT INTO payroll.leave_encashments (
    employee_id, calculation_month, period_in_months, days_encashed,
    basic_salary, amount, payment_status, paid_at, payroll_run_id,
    description, created_by, updated_by
)
SELECT
    employee_id, calculation_month, period_in_months, days_encashed,
    basic_salary, amount, payment_status,
    CASE WHEN payment_status = 'PAID' THEN paid_at ELSE NULL END,
    NULL,
    description, 'legacy-migration', 'legacy-migration'
FROM ranked;

COMMIT;

-- summary
SELECT 'payroll_runs'                AS metric, count(*) FROM payroll.payroll_runs                UNION ALL
SELECT 'payslips',                             count(*) FROM payroll.payslips                    UNION ALL
SELECT 'payslips PAID',                        count(*) FROM payroll.payslips WHERE status='PAID' UNION ALL
SELECT 'payslips HELD',                        count(*) FROM payroll.payslips WHERE status='HELD' UNION ALL
SELECT 'payslip_lines',                        count(*) FROM payroll.payslip_lines               UNION ALL
SELECT 'permanent_advances',                   count(*) FROM payroll.permanent_advances          UNION ALL
SELECT 'permanent_advance_deductions',        count(*) FROM payroll.permanent_advance_deductions UNION ALL
SELECT 'short_term_advance_batches',          count(*) FROM payroll.short_term_advance_batches  UNION ALL
SELECT 'short_term_advances',                 count(*) FROM payroll.short_term_advances         UNION ALL
SELECT 'leave_encashments',                   count(*) FROM payroll.leave_encashments;
