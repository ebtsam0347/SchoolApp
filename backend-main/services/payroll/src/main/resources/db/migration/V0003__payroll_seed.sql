-- payroll-service seed data: catalog rows + a single CURRENT fiscal year +
-- baseline statutory rates. Idempotent — re-running this migration on a
-- partially-seeded schema is safe.

-- ---------------------------------------------------------------------
-- Salary component catalog. INCOME_TAX, LOAN, ADVANCE come pre-seeded
-- because every HR onboarding flow needs them. HRA / CONVEYANCE /
-- MEDICAL are common Pakistan SME allowances; HR can disable any they
-- don't use.
-- ---------------------------------------------------------------------

insert into salary_components (code, name, type, calc_type, default_amount, display_order, active, created_by, updated_by)
values
    ('HRA',          'House Rent Allowance', 'EARNING',   'FLAT',      null, 10, true, 'system', 'system'),
    ('CONVEYANCE',   'Conveyance Allowance', 'EARNING',   'FLAT',      null, 20, true, 'system', 'system'),
    ('MEDICAL',      'Medical Allowance',    'EARNING',   'FLAT',      null, 30, true, 'system', 'system'),
    ('INCOME_TAX',   'Income Tax',           'DEDUCTION', 'FLAT',      null, 90, true, 'system', 'system'),
    ('LOAN',         'Loan Repayment',       'DEDUCTION', 'FLAT',      null, 95, true, 'system', 'system'),
    ('ADVANCE',      'Salary Advance',       'DEDUCTION', 'FLAT',      null, 96, true, 'system', 'system'),
    ('OTHER',        'Other',                'DEDUCTION', 'FLAT',      null, 99, true, 'system', 'system')
on conflict (code) do nothing;

-- ---------------------------------------------------------------------
-- Fiscal year. Pakistan FY = July–June. Seed the FY that covers today
-- as CURRENT. The migration runs at any time of year, so derive
-- start/end dates from the current month.
-- ---------------------------------------------------------------------

insert into fiscal_years (code, start_date, end_date, status, created_by, updated_by)
select
    case
        when extract(month from current_date) >= 7 then
            to_char(current_date, 'YYYY') || '-' || to_char(current_date + interval '1 year', 'YY')
        else
            to_char(current_date - interval '1 year', 'YYYY') || '-' || to_char(current_date, 'YY')
    end as code,
    case
        when extract(month from current_date) >= 7 then
            make_date(extract(year from current_date)::int, 7, 1)
        else
            make_date((extract(year from current_date) - 1)::int, 7, 1)
    end as start_date,
    case
        when extract(month from current_date) >= 7 then
            make_date((extract(year from current_date) + 1)::int, 6, 30)
        else
            make_date(extract(year from current_date)::int, 6, 30)
    end as end_date,
    'CURRENT' as status,
    'system' as created_by,
    'system' as updated_by
on conflict (code) do nothing;

-- ---------------------------------------------------------------------
-- Baseline statutory config — open-ended row effective from today.
-- HR adjusts via POST /statutory/config when rates change.
--   EOBI: PKR 1,500 / month employee share (PKR 250 above 2024 hike;
--         HR overrides at deploy time if outdated).
--   PESSI: 6% of gross capped at PKR 25,000 — Punjab provincial rate.
--   Gratuity: 12 months min service, 6 months partial-year threshold
--             (Standing Orders defaults).
-- ---------------------------------------------------------------------

insert into statutory_config (
    effective_from, effective_to, eobi_amount, pessi_rate_percent, pessi_salary_cap,
    gratuity_min_service_months, gratuity_partial_year_threshold_months, notes,
    created_by, updated_by
)
select current_date, null, 150000, 6.00, 2500000, 12, 6,
       'Baseline rates seeded at install. HR adjusts via /statutory/config.',
       'system', 'system'
where not exists (select 1 from statutory_config);
