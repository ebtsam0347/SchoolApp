# Legacy WebHRMS → FSHRMS migration map

This is the spec for the ETL that lifts data out of the `staging.*` schema
(produced by `replicate.py`) and into the FSHRMS service schemas
(`auth`, `employee`, `attendance`, `payroll`). It captures every mapping
decision made during the comparison sessions; the actual migration script
should treat this file as the source of truth.

Read order: top to bottom. Sections are in dependency order — earlier
loads are referenced as FK targets by later ones. Don't reorder.

---

## 0. Pre-flight

Before any ETL runs:

1. Run all Flyway migrations (`./mvnw -pl employee,attendance,payroll spring-boot:run`
   for each service once, or `flyway migrate` per service). Stop the
   services before the ETL — the loader writes to schemas the services
   would otherwise be reading.
2. Confirm the seeded catalogs exist (provinces, cities, qualifications,
   skills, leave_types, salary_components, fiscal_years,
   payroll_config, disabilities). The ETL extends these — it does not
   re-seed them. (`statutory_config` was renamed `payroll_config` by
   payroll `V0020`.)
3. **Run `000_bootstrap.sql`.** It creates the scratch `migration`
   schema, the legacy-id lookup table `migration.id_map`, and
   `migration.ess_initial_passwords`. Run it before `00_reset.sql` —
   reset truncates both tables, so they have to exist first.

   `id_map` is why the bootstrap is needed at all: most legacy rows
   reference each other by integer id (`employee_id`, `department_id`,
   `leaves_id`), while FSHRMS uses bigint identity columns that won't
   match. Every load step writes to `id_map` after inserting its target
   rows so later steps can resolve FKs. The schema stays for the duration
   of the migration and is dropped at sign-off (§8).

   On a server host the ETL runs as the env role (`uat` / `prod`), which
   needs `CREATE` on the **database** to make the schema — owning the
   service schemas does not confer it. Same grant `pg_trgm` needs; see
   CLAUDE.md.

4. **Money convention.** Legacy stores money in PKR with a `.0` decimal
   suffix (e.g. `35000.0`). FSHRMS stores `numeric(15,2)` rupees.
   `target = (ceil(source::numeric * 100) / 100.0)::numeric(15,2)` — always
   round **up** at scale 2 (per CLAUDE.md / `Money` helper). This is what
   `03_payroll_data.sql` and `06_payroll_transactions.sql` already do; the
   earlier `bigint` paisa plan was dropped in payroll `V0008__money_decimal.sql`.

5. **Boolean convention.** Legacy `True` / `False` / `''` strings →
   `(value = 'True')`.

6. **Datetime convention.** Legacy ISO strings with offsets → `timestamptz`.
   Bare ISO dates → `date`. Empty string → `NULL`.

7. **`created_by` / `updated_by` audit columns.** Use `'legacy-migration'`
   for every row inserted by ETL; `created_at` / `updated_at` come from
   the source row's `entrydatetime` / `lastupdatedate` when available,
   else `now()`.

---

## 1. Catalogs (load first)

These have no FK dependencies on the other domains and seed the lookups
that everything else references.

### 1.1 `employee.cities`

| Target | Source | Notes |
|---|---|---|
| `employee.cities.code` | derive from `name` (uppercase, first 3 chars or hand-mapped) | The seed already has SKT/LHE/GUJ/etc. — only insert legacy cities that don't already match. |
| `employee.cities.name` | `staging.apis_city.name` | Trim. |
| `employee.cities.province_id` | hand-tagged during ETL | All legacy rows are PK; default to PUNJAB unless the city name is in the SD/KP/BL/ICT/AJK/GB list. |

`id_map(source_table='apis_city', source_id=staging.apis_city.id, …)`.

### 1.2 `employee.departments`

| Target | Source | Notes |
|---|---|---|
| `code` | derive from `name` (snake_case + truncate 32) | `deptcode` is blank in legacy. |
| `name` | `staging.apis_department.name` | |
| `head_id` | NULL on first pass; backfilled from `staging.apis_department.hodname_id` after `employees` is loaded (resolve via `id_map('apis_employeinfo')`). | |
| `active` | `(staging.apis_department.status = 'True')` | |

### 1.3 `employee.sections`

| Target | Source | Notes |
|---|---|---|
| `code` | `staging.apis_section.sectioncode` (truncate 32) | |
| `name` | `staging.apis_section.name` | |
| `department_id` | `id_map('apis_department', staging.apis_section.department_id)` | |
| `head_id` | NULL on first pass; no obvious legacy field. | |

### 1.4 `employee.designations`

| Target | Source | Notes |
|---|---|---|
| `code` | derive from `name` | |
| `title` | `staging.apis_designations.name` | |
| `category` | `staging.apis_designations.designdetail` → `'Direct'` ⇒ `DIRECT`, `'In-Direct'` ⇒ `INDIRECT`, else NULL | Schema added in V0005. |

### 1.5 `employee.grades`

| Target | Source | Notes |
|---|---|---|
| `code` | `staging.apis_addgrade.name` (E-1, M-2, …) | |
| `name` | `staging.apis_addgrade.description` | "GM/Dir/Pres" etc. |
| `level` | derive from code prefix (E-x → 1, M-x → 2, …) or hand-mapped | Legacy has no integer level. |

### 1.6 `employee.qualifications`

Already seeded with PK-standard qualifications. Insert rows from
`staging.apis_addqualification` whose name doesn't already match.

### 1.7 `employee.disabilities`

Already seeded with 8 ICF categories. The legacy `staging.apis_adddisability`
has 78 specific descriptions ("Right Arm Polio", etc.) that go into
`employee_disabilities.description`, not as catalog rows. Build a
classifier:

| Legacy description matches | → catalog code |
|---|---|
| `*polio*`, `*paralyzed*`, `*leg*`, `*arm*`, `*foot*`, `*body*` | PHYSICAL |
| `*deaf*`, `*hearing*` | HEARING |
| `*dumb*`, `*speech*`, `*mute*` | SPEECH |
| `*blind*`, `*visual*`, `*eye*` | VISUAL |
| else | OTHER |

`Deaf & Dumb` → MULTIPLE (HEARING + SPEECH).

### 1.8 `payroll.banks`

| Target | Source | Notes |
|---|---|---|
| `code` | derive from `name` (snake_case + truncate 32) | |
| `name` | `staging.apis_bankdetail.name` | |
| `branch` | parse from `hrmbankaddress` if it includes a branch | Best-effort; can leave NULL. |
| `address` | `staging.apis_bankdetail.hrmbankaddress` | |
| `account_number` | `staging.apis_bankdetail.hrmbankaccno` | This is the **company's** account at that bank. |
| `iban` | NULL (legacy didn't capture) | HR fills in post-migration. |
| `account_title` | constant `'Forward Sports Pvt Ltd'` | Single-tenant; same title on all rows. |
| `active` | `(staging.apis_bankdetail.hrmbankstatus = 'True')` | |

### 1.9 `attendance.shifts`

| Target | Source | Notes |
|---|---|---|
| `code` | derive from `name` (the legacy `shiftcode` is just "Day"/"Night" for all 9; uses name) — e.g. `DAY_NORMAL_745`, `NIGHT_AFTERNOON`, `SECURITY_1`. | |
| `name` | `staging.apis_shift.name` | |
| `start_time` | `staging.apis_shift.shiftst` | |
| `end_time` | `staging.apis_shift.shiftet` | |
| `break_start_time` | `staging.apis_shift.breakst` | |
| `break_end_time` | `staging.apis_shift.breaket` | |
| `grace_minutes` | constant `10` | Legacy didn't track. |
| `friday_extra_minutes` | `staging.apis_shift.fridayextrabreak::int` | |

### 1.10 `attendance.holidays`

| Target | Source | Notes |
|---|---|---|
| `holiday_date` | `staging.apis_hrm_hr_holidays.holidaydate` | |
| `name` | `staging.apis_hrm_hr_holidays.name` | |
| `description` | `staging.apis_hrm_hr_holidays.holidayremarks` | |
| `type` | classify from name: matches `Eid|Ramadan|Iqbal|Quaid|Pakistan|Independence|Kashmir|Labour|Christmas` ⇒ `NATIONAL`; else `COMPANY`. | Legacy has no type. |

### 1.11 `attendance.leave_types`

V0007 already updates `SICK → M1` (8 days) and adds M2, SPECIAL,
FACTORY_DUTY, REST_DAY. Don't insert from legacy — the seed is the
authoritative list. ETL just builds a name→id resolver:

| Legacy `apis_hrm_hr_leavesquota.name` | → `code` |
|---|---|
| Medical / M1 | M1 |
| M2 | M2 |
| Casual | CASUAL |
| Annual | ANNUAL |
| Special | SPECIAL |
| Factory Duty | FACTORY_DUTY |
| Maternity Leaves | MATERNITY |
| Rest Day | REST_DAY |
| Casual Unpaid | UNPAID |

The resolver is the FK-translator for `staging.payroll_payroll_leaves.leaves_id`
during the leaves load.

### 1.12 `attendance.attendance_devices`

| Target | Source | Notes |
|---|---|---|
| `code` | derive from `name` (snake_case + truncate 32) | |
| `name` | `staging.payroll_payroll_deviceinfo.name` | |
| `location` | `staging.payroll_payroll_deviceinfo.location` (mostly empty — derive from `name` if blank) | |
| `device_ip` | `staging.payroll_payroll_deviceinfo.deviceip` | Cast text → inet. |
| `active` | `(status = 'True')` | |

### 1.13 `payroll.fiscal_years`

Insert each `staging.apis_financialperiod` row that's not already in
`fiscal_years`:

| Target | Source | Notes |
|---|---|---|
| `code` | `staging.apis_financialperiod.name` reformatted (`FY 2025-26` → `FY2025-26`, truncate 7) | |
| `start_date` | `start_date` | |
| `end_date` | `end_date` | |
| `status` | derive: row with greatest `end_date >= today` → `CURRENT`; future → `FUTURE`; past → `CLOSED`. Exactly one CURRENT row. | |

---

## 2. Employee data

Depends on §1. Load in this order: `employees` (root), then everything
keyed by employee.

### 2.1 `employee.employees`

The hardest table — pulls from both `apis_employeinfo` and
`apis_empcompanyinfo` joined on `employeeinfo.id =
empcompanyinfo.employee_id`.

| Target | Source | Notes |
|---|---|---|
| `employee_code` | `staging.apis_employeinfo.cardno` | The legacy "card number" is the `employee_code` in FSHRMS — confirmed by user. Pad to 6 digits if needed. |
| `full_name` | `apis_employeinfo.name` | Trim. |
| `cnic` | `apis_employeinfo.cnicno` | Strip dashes; keep 13 digits. |
| `cnic_issue_date` | `apis_employeinfo.cnicissuancedate` | |
| `cnic_expiry_date` | `apis_employeinfo.cninexpirydate` | |
| `ntn` | `apis_empcompanyinfo.ntnno` | |
| `insurance_number` | `apis_empcompanyinfo.insuranceno` | |
| `ssc_number` | `apis_empcompanyinfo.sscno` | |
| `eobi_number` | `apis_empcompanyinfo.eobino` | |
| `date_of_birth` | `apis_employeinfo.dateofbirth` | |
| `gender` | `apis_employeinfo.gender` | uppercase, normalize to `MALE`/`FEMALE`/`OTHER`. |
| `marital_status` | `apis_employeinfo.maritalstatus` | uppercase, normalize. |
| `father_name` | `apis_employeinfo.fathername` | |
| `mother_name` | `apis_employeinfo.mothername` | |
| `spouse_name` | `apis_employeinfo.husbandname` (legacy only stored husband; wife is implied by gender). | |
| `religion` | `apis_employeinfo.religion` | |
| `blood_group` | `apis_employeinfo.bloodgroup` | |
| `personal_email` | NULL | Legacy didn't have. |
| `work_email` | `apis_employeinfo.official_email_address` | |
| `phone` | `apis_employeinfo.contactno` | |
| `permanent_address` | `apis_employeinfo.emppermanentaddress` | |
| `current_address` | `apis_employeinfo.empcurrentaddress` | |
| `city_id` | `id_map('apis_city', apis_employeinfo.empcity_id)` | |
| `country` | constant `'PK'` | |
| `joining_date` | `apis_empcompanyinfo.dateofjoning` | |
| `confirmation_date` | `apis_empcompanyinfo.dateofpermanent` | |
| `employment_status` | derive: `apis_empcompanyinfo.empstatus = 'Active'` → `ACTIVE`; `'Resigned'`/`'Terminated'` → `TERMINATED` (and create `employee_exit` row); `'Suspended'` → `SUSPENDED`. | |
| `employment_type` | `apis_empcompanyinfo.employeetype` → uppercase + normalize (`Permanent`→PERMANENT, `Contract`→CONTRACT, `Internship`→INTERN). | |
| `department_id` | `id_map('apis_department', apis_empcompanyinfo.department_id)` | |
| `section_id` | `id_map('apis_section', apis_empcompanyinfo.section_id)` | |
| `designation_id` | `id_map('apis_designations', apis_empcompanyinfo.designation_id)` | |
| `grade_id` | `id_map('apis_addgrade', apis_empcompanyinfo.gradeid_id)` | |
| `manager_id` | `id_map('apis_employeinfo', apis_empcompanyinfo.empreportto_id)` — **two-pass**: pass 1 inserts everyone with NULL manager_id; pass 2 updates from the resolved id_map. | |
| `profile_picture_key` | NULL | The legacy `emppic` is a path to a local file; copy to MinIO separately, set the key after upload. |
| `created_at` | `apis_employeinfo.creationdate` (fall back to `entrydatetime`) | |
| `created_by` | `'legacy-migration'` | |

Then write `id_map('apis_employeinfo', source_id, 'employees', target_id)`.

### 2.2 `employee.employee_history`

For each employee, write **one row** representing the current assignment
(no historical chain — change_history tables are skipped per `tables.yml`):

| Target | Source | Notes |
|---|---|---|
| `employee_id` | from `employees` insert | |
| `effective_from` | `apis_empcompanyinfo.depteffectivedate` (fall back to `joining_date`) | |
| `effective_to` | NULL (current row) | |
| `department_id` / `section_id` / `designation_id` / `grade_id` / `manager_id` | same as on `employees` | Snapshot. |
| `change_reason` | `'ONBOARD'` | |
| `notes` | `'Migrated from WebHRMS — current assignment only; historical chain not migrated.'` | |

### 2.3 `employee.employee_exits`

For each `apis_empcompanyinfo` row with `empstatus IN ('Resigned','Terminated')`:

| Target | Source | Notes |
|---|---|---|
| `employee_id` | resolve from `id_map` | |
| `exit_type` | `Resigned` → `RESIGNATION`, `Terminated` → `TERMINATION`, `Retired` → `RETIREMENT`. | |
| `last_working_day` | `apis_empcompanyinfo.leavedate` | |
| `notice_start_date` | `apis_empcompanyinfo.leavingnoticedate` | |
| `reason` | `apis_empcompanyinfo.leavedesc` | |
| `status` | `'COMPLETED'` (these are historical) | |
| `completed_at` | `apis_empcompanyinfo.leavedate::timestamptz` | |

### 2.4 `employee.employee_qualifications`

Source: `staging.apis_empeducation` (367 rows). FK by `employee_id`,
match to `qualifications` by `apis_addqualification` resolver.

### 2.5 `employee.employee_certifications`

Source: `staging.apis_certificate` (110 rows). One row per certificate
with `course_name → name`, `institute_name → issuing_body`,
`issue_date → issue_date`, `course_level → credential_id` (free-text).

### 2.6 `employee.employee_prior_experience`

Source: `staging.apis_experience` (46 rows). Direct map.

### 2.7 `employee.employee_disabilities`

For each `staging.apis_employeinfo` row with non-null `disability_id`:

| Target | Source | Notes |
|---|---|---|
| `employee_id` | resolve | |
| `disability_id` | classify legacy description (see §1.7) | |
| `description` | `apis_adddisability.name` joined via `apis_employeinfo.disability_id` | The 78 legacy strings land here. |
| `severity` | NULL (legacy didn't capture) | |
| `document_key` | NULL | |

---

## 3. Payroll-side per-employee tables

Depends on §2.

### 3.1 `payroll.employee_payroll_settings`

One row per employee.

| Target | Source | Notes |
|---|---|---|
| `employee_id` | resolve | |
| `hire_date` | `apis_empcompanyinfo.dateofjoning` | |
| `eobi_enrolled` | `(apis_empcompanyinfo.eobino IS NOT NULL AND eobino <> '')` | |
| `payment_mode` | `apis_empcompanyinfo.paymenttype` → `Bank` ⇒ `BANK_TRANSFER`, `Cash` ⇒ `CASH`. | |
| `gratuity_start_date` | `apis_empcompanyinfo.graduitydate` | V0005 added this column. |
| `senior_allowance_start_date` | `apis_empcompanyinfo.senallowdate` | NULL when `senallowancestatus = 'No'` or empty. |

### 3.2 `payroll.employee_bank_details`

For employees with `payment_mode = BANK_TRANSFER`:

| Target | Source | Notes |
|---|---|---|
| `employee_id` | resolve | |
| `bank_id` | `id_map('apis_bankdetail', apis_empcompanyinfo.bank_id)` | |
| `branch` | NULL | |
| `account_title` | `apis_employeinfo.name` | Legacy didn't have separate; assume same as employee name. |
| `iban` | NULL | HR fills in post-migration via the upsert endpoint. |
| `account_number` | `apis_empcompanyinfo.bankaccountno` | Encrypted at rest by the commons helper. |

### 3.3 `payroll.employee_salaries`

For each employee, one **current** row from `apis_salary_combined`
(one row per employee, the active comp snapshot):

| Target | Source | Notes |
|---|---|---|
| `employee_id` | resolve | |
| `effective_from` | `apis_salary_combined.effectedfromdate` | |
| `effective_to` | NULL | Current row. |
| `basic` | `apis_salary_combined.basicsalary * 100`, ceil | |

`apis_salary_combined.oldsalary` and `icrementdate` could feed historical
chain rows but **skip** — only migrate current.

### 3.4 `payroll.employee_salary_components`

For each non-zero allowance / deduction column on `apis_salary_combined`,
write one row keyed to a `salary_components` catalog code:

| Legacy column | Target component code | Type |
|---|---|---|
| `mealallowance` | `MEAL` | EARNING |
| `travelallowance` | `TRAVEL` | EARNING |
| `educationallowance` | `EDUCATION` | EARNING |
| `medicalallowance` | `MEDICAL` | EARNING |
| `accomodationallowance` | `ACCOMMODATION` | EARNING |
| `extraallowance` | `EXTRA` | EARNING |
| `specialallowance` | `SPECIAL` | EARNING |
| `senallownace` | `SENIORITY` | EARNING |
| `emprewardpolicyamount` | `REWARD_POLICY` | EARNING |
| `taxdeduction` | `INCOME_TAX` | DEDUCTION |
| `rentdeduction` | `RENT` | DEDUCTION |
| `canteendeducts` | `CANTEEN` | DEDUCTION |
| `unionfund` | `UNION_FUND` | DEDUCTION |

`salary_components` catalog must contain each of these codes — extend
the seed (V0003 in payroll) before this load runs.

`amount = legacy_value * 100` (paisa, ceil). Skip rows where the value
is zero or empty.

PESSI is **not** a salary_components row — it's `statutory_config`
rate × employee gross at compute time.

EOBI similarly — driven by `eobi_enrolled` + statutory rate, not a
flat per-employee amount.

---

## 4. Attendance & leaves

Depends on §1 (catalogs) + §2 (employees).

### 4.1 `attendance.employee_shifts`

Current shift assignment per employee:

| Target | Source | Notes |
|---|---|---|
| `employee_id` | resolve | |
| `shift_id` | `id_map('apis_shift', apis_empcompanyinfo.shift_id)` | |
| `effective_from` | `apis_empcompanyinfo.shifteffectivedate` | |
| `effective_to` | NULL | |

### 4.2 `attendance.attendance_punches`

Source: `staging.tbl_ATT_LOG` (currently disabled in `tables.yml` —
enable before running this step; ~6.5M rows so plan accordingly).

Inspect the actual columns once enabled. Pattern: one row per device
event with `(employee_id, punched_at, device_id, source=BIOMETRIC)`.

`device_id` = `id_map('payroll_payroll_deviceinfo', tbl_ATT_LOG.machineid_or_ip)`.

### 4.3 `attendance.attendance_records`

Source: `staging.payroll_payroll_attendance` (211K rows, 60-day window).

| Target | Source | Notes |
|---|---|---|
| `employee_id` | resolve via `cardno` → `employees.employee_code` lookup | |
| `attendance_date` | `attdate` | |
| `check_in` | `in_time_datetime` | |
| `check_out` | `out_time_datetime` | |
| `effective_shift` | `id_map('apis_shift', shift_id)` | |
| `shift_start_time` | `shiftst` | Snapshot. |
| `shift_end_time` | `shiftet` | |
| `shift_break_start_time` | `breakst` | |
| `shift_break_end_time` | `breaket` | |
| `shift_grace_minutes` | constant `10` | |
| `status` | derive: punches present + within shift ⇒ `PRESENT`, no punches + Sunday ⇒ `WEEKEND`, no punches + holiday ⇒ `HOLIDAY`, no punches + approved leave_request_day ⇒ `LEAVE`, else `ABSENT`. | The `status` column is FSHRMS-specific — legacy never had a unified one. |
| `late_minutes` / `early_minutes` / `worked_minutes` | recompute from check_in/out + shift snapshot — don't trust legacy's `duration` field. | `AttendanceRecomputeService` logic; ETL can call the same helper. |
| `ot_normal_minutes` / `ot_extra_minutes` / `ot_special_minutes` | derive from worked_minutes against shift expected, gated on an APPROVED `overtime_requests` row for that day. | Legacy's three boolean OT flags + `duration` translate as: `if otstatus = True ⇒ first 120 min into ot_normal, remainder into ot_extra; if specialotstatus ⇒ all into ot_special`. |
| `source` | `manual = True` ⇒ `OPERATOR`, else `BIOMETRIC` | |

### 4.4 `attendance.leave_balances`

Computed (see `08_leave_balances.sql`). Legacy keeps quotas only at the
leave-type level (`apis_hrm_hr_leavesquota`), so balances are derived.
One row per (ACTIVE employee × leave_type) for the current calendar year
(`LeaveBalanceService` keys by calendar year, not FY).

- `allocated` = `leave_types.default_per_year`.
- `used` = sum(days) over APPROVED `leave_requests` whose `to_date` falls
  in the target year, capped at `allocated` (legacy occasionally let
  employees overdraw a quota — FSHRMS doesn't model that).
- `pending` = 0 — the loader sets every imported request to APPROVED, so
  there's nothing pending to count.
- `carried` = 0 — legacy didn't capture explicit carryover.

Skips TERMINATED employees (no fresh allocation needed).

### 4.5 `attendance.leave_requests` + `leave_request_days`

Source: `staging.payroll_payroll_leaves` (22.6K rows, 60-day window).

| Target | Source | Notes |
|---|---|---|
| `request_code` | new code from `request_code_sequence`, prefix `LR-` and year derived from `entrydatetime`. | |
| `employee_id` | resolve via `cardno` | |
| `leave_type_id` | resolve via legacy `leaves_id → apis_hrm_hr_leavesquota.name → leave_types.code` (see §1.11) | |
| `from_date` / `to_date` | `leavestdate` / `leaveeddate` | |
| `half_day` | `(halfdaystatus = 'True')` | |
| `half_day_part` | `halfdaytime` → `1st Half` ⇒ `FIRST_HALF`, `2nd Half` ⇒ `SECOND_HALF`. | |
| `days` | `totaldays` | |
| `reason` | `leaveremarks` (fall back to `appovalremarks`) | |
| `status` | derive from legacy boolean cascade: `leavestatus AND hrstatus AND payrollstatus = True/blank` ⇒ `APPROVED`; any explicit False ⇒ `REJECTED`; else `PENDING`. | |

Then for each working day in `[from_date, to_date]` (skip Sundays +
holidays), insert a `leave_request_days` row with `status = APPROVED`
(if the parent is APPROVED) and `decided_by` resolved from
`approvedby_id` / `approvedbypayroll_id`.

**Approval workflow rows (`approval_steps`)**: synthesize 2 stages per
request — MANAGER + HR (or HR + PAYROLL) — using the legacy decision
columns:

| Stage | Role | `decided_by` source | `decided_at` source |
|---|---|---|---|
| 1 | MANAGER | `approvedby_id` | `approveddate` |
| 2 | HR | `approvedbyhr_id` | `hrstatusdate` |

Skip stage 3 (payroll) — FSHRMS doesn't have a payroll approval stage
on leaves; payroll just consumes the APPROVED state.

### 4.6 `attendance.short_leave_requests`

Source: `staging.payroll_payroll_shortleaves` (1,175 rows in 60d).

| Target | Source | Notes |
|---|---|---|
| `request_code` | sequence, prefix `SL-` | |
| `employee_id` | resolve via `cardno` | |
| `leave_date` | `leavedate` | |
| `start_time` | `starttime` | |
| `end_time` | `endtime` | |
| `reason` | `slremarks` | |
| `status` | `(approvestatus = 'True' AND hrstatus = 'True')` ⇒ `APPROVED`, else logic similar to leaves. | |

Synthesize 1 `approval_steps` row (HR stage) per request.

---

## 5. Payroll transactions

Depends on §2 + §3 + §4.

### 5.1 `payroll.payroll_runs`

For each distinct `salarymonth` in
`staging.account_acc_salary_transactions` (60d window has 1):

| Target | Source | Notes |
|---|---|---|
| `fiscal_year_code` | resolve via `financialperiod_id` | |
| `period_year` / `period_month` | derive from `salarymonth` | |
| `status` | `'POSTED'` (legacy rows are historical and final) | |
| `attendance_locked` | `true` | |
| `posted_at` | `transactiondate` of any line in the period | |

### 5.2 `payroll.payslips` + `payslip_lines`

One `payslips` row per `staging.account_acc_salary_transactions` row.

| Target | Source | Notes |
|---|---|---|
| `payslip_code` | `'PSL-' || period_year || '-' || lpad(period_month::text,2,'0') || '-' || employee_code` | |
| `payroll_run_id` | from §5.1 | |
| `employee_id` | resolve via `cardno` | |
| `employee_code` | `cardno` | |
| `period_year` / `period_month` | from §5.1 | |
| `fiscal_year_code` | from §5.1 | |
| `basic` | `basicsalary * 100`, ceil | |
| `allowances` | `totaladds * 100` minus `arrearsamt * 100` minus `overtimepaid * 100` (allowances only, OT/arrears broken out separately) | |
| `gross` | `grosssalary * 100`, ceil | |
| `deductions` | `totalsubtracts * 100` minus `taxdeductions * 100` minus `eobi * 100` | |
| `tax` | `taxdeductions * 100` | |
| `eobi` | `eobi * 100` | |
| `pessi_employer` | `pessicontribution * 100` | |
| `net` | `payablesalary * 100` | |
| `lop_days` | `unpaidleaves` | |
| `working_days` | `workingdaysinmonth` | V0007. |
| `paid_days` | `totalpayabledays` | |
| `absent_days` | `totalabsentees` | |
| `holidays_in_period` | `holidaysinmonth` | |
| `sundays_in_period` | `sundaysinmonth` | |
| `ot_hours` | `overtimehours` | |
| `ot_amount` | `overtimepaid * 100` | |
| `status` | `paidunpaidstatus = 'Paid'` ⇒ `PAID`; else `HELD` (with `held_reason = 'migrated as unpaid from legacy'`). | |
| `generated_at` | `transactiondate` | |

`payslip_lines`: one row per non-zero allowance/deduction column,
mapping to the same component codes used in §3.4. Use
`calc_type = FLAT`. Order:

```
display_order = 10 — basic (skip; on payslip header)
display_order = 20+ — allowance lines (MEAL, TRAVEL, …)
display_order = 50+ — OT/arrears
display_order = 100+ — deduction lines (EOBI, INCOME_TAX, RENT, …)
display_order = 200+ — advance deductions (LONG_TERM_ADV, SHORT_TERM_ADV)
```

### 5.3 `payroll.permanent_advances`

Source: `staging.account_acc_permanent_advances` (~340 rows in 60d).

| Target | Source | Notes |
|---|---|---|
| `request_code` | sequence, prefix `PA-` | |
| `employee_id` | resolve via `cardno` (or `employee_id` lookup, but cardno is more reliable) | |
| `amount` | `advamt * 100`, ceil | |
| `monthly_deduction` | `dedrate * 100`, ceil | |
| `bonus_offset` | `dedagainstbonus * 100` | |
| `annual_leave_offset` | `dedagainstaleaves * 100` | |
| `gratuity_at_request` | `graduity * 100` | |
| `receiving_date` | `receivingdate` | |
| `description` | `description` | |
| `status` | derive: `appovalstatus AND accountsstatus AND hrstatus AND receivingdate IS NOT NULL` ⇒ `DISBURSED`; if a deduction with `remaining_balance = 0` exists ⇒ `CLOSED`; if any False ⇒ `REJECTED`; else `PENDING`. | |
| `hr_decided_by` / `hr_decided_at` / `hr_note` | `approvedbyhr_id` / `hrstatusdate` / `appovalremarks` | |
| `accounts_decided_by` / `accounts_decided_at` / `accounts_note` | `approvedbyaccounts_id` / `accountsstatusdate` / NULL | |
| `rejection_reason` | `rejectdescription` | |

### 5.4 `payroll.permanent_advance_deductions`

Source: `staging.account_acc_permanent_advance_deductions` (~5K in 60d).

| Target | Source | Notes |
|---|---|---|
| `advance_id` | `id_map('account_acc_permanent_advances', permanentadvanceid_id)` | |
| `payroll_run_id` | match `transactiondate` to a `payroll_runs` row by period | |
| `deducted_amount` | `padvdedamount * 100`, ceil | |
| `bonus_deduction` | `bonusdeduction * 100` | |
| `annual_leave_deduction` | `annualleavededuction * 100` | |
| `remaining_balance` | `ltaremaining * 100` | |
| `transaction_date` | `transactiondate` | |
| `description` | `description` | |

### 5.5 `payroll.short_term_advance_batches` + `short_term_advances`

Source: `staging.account_acc_short_term_advances_head` (~170 in 60d) +
`staging.account_acc_short_term_advances` (~6K in 60d).

Batch:

| Target | Source | Notes |
|---|---|---|
| `request_code` | sequence, prefix `STA-` | |
| `department_id` | resolve | |
| `target_year` / `target_month` | from `monthadv` / `yearadv` | |
| `total_amount` | `totalamount * 100` | |
| `total_employees` | `totalemployees::int` | |
| `status` | same derivation as `permanent_advances` | |
| HR/accounts decision cols | same pattern | |

Detail:

| Target | Source | Notes |
|---|---|---|
| `batch_id` | `id_map('account_acc_short_term_advances_head', headid_id)` | |
| `employee_id` | resolve via `cardno` | |
| `amount` | `amount * 100` | |
| `target_year` / `target_month` | from `monthadv` / `yearadv` | |
| `status` | `(appovalstatus AND accountsstatus AND hrstatus = 'True')` ⇒ `APPROVED` (or `DEDUCTED` if a payroll run has run for that period); else `PENDING` / `REJECTED`. | |

### 5.6 `payroll.leave_encashments`

Source: `staging.account_account_annualleavetransactions` (~12K in 60d).

| Target | Source | Notes |
|---|---|---|
| `employee_id` | resolve via `cardno` (or `employee_id`) | |
| `calculation_month` | `calculationmonth` | |
| `period_in_months` | `periodinmonths::int` | |
| `days_encashed` | `paydays` | |
| `basic_salary` | `basicsalary * 100`, ceil | |
| `amount` | `dueamount * 100`, ceil | |
| `payment_status` | `havepaidornot = 'True'` ⇒ `PAID`, else `PENDING`. | |
| `paid_at` | `transactiondate` (when PAID) | |
| `payroll_run_id` | NULL (legacy doesn't link to a run) | |
| `description` | `description` | |

### 5.7 Bonus events / amounts / installments

Source: `staging.account_account_bonusno*` + `bonustransactions`. Map
into `bonus_events` (header) + `bonus_event_amounts` (per-employee
amounts) + `off_cycle_payments` (the actual paid rows). Defer detailed
mapping to a follow-up — bonus has a richer event/installment shape
that needs its own walk-through.

---

## 6. Auth

Last to load. Depends on `employees` being in place.

`auth.users` rows are normally created via the employee→ESS provisioning
saga. The migration writes them directly instead (`07_ess_provisioning.sql`)
— thousands of sequential `POST /admin/employees/{id}/grant-ess` calls is
not a sensible cutover step, and the saga's only side effects are the
`auth.users` row, the EMPLOYEE role grant and the `employees.user_id`
write-back, all three of which 07 does in one transaction.

Eligible: ACTIVE employees whose `apis_employeinfo.loginname_id` is set
(they had a login in the legacy Django auth system). TERMINATED employees
are skipped per the FSHRMS termination policy.

**Passwords.** Every user gets an independent random 10-character password
(unambiguous alphabet — no I/O/0/1), bcrypt cost 12, with
`must_change_pw = true` so it only survives first login. The plaintext
lands in `migration.ess_initial_passwords`, which is the only copy; 07 §6.3
has the CSV export for HR and the `DROP TABLE` that must follow it. Nothing
in the migration touches `admin` or `poller-svc` — those credentials come
from the sealed secret.

Budget ~12 minutes per 2,500 users (measured: ~0.3s per hash): bcrypt cost
12 is deliberately slow and 07 hashes every user in one transaction.

Don't try to migrate legacy `auth_user` / `django_session` — the
legacy auth system is Django, not portable.

---

## 7. Skipped (out of scope)

These legacy tables are intentionally not migrated. If a future request
brings them back into scope, revisit.

| Domain | Tables | Why |
|---|---|---|
| Assets | `Assets_*` | FSHRMS scope excludes asset management. |
| Document management | `Documentum_*` | Empty in legacy + out of scope. |
| Performance evaluation | `Evaluation_*`, `Eva_Comp_*`, `apis_evaluationkpi*`, `apis_rewardpolicy` | Out of FSHRMS scope. |
| Recruitment / requisitions | `apis_requisition*`, `apis_noticeinfo`, `apis_announcement` | Out of FSHRMS scope. |
| Welfare / facility ops | `apis_blooddonner`, `apis_bus*`, `apis_card*`, `tbl_Prayer_*`, `tbl_Medical_*`, `Tbl_Med_Wastage`, `FairPrice_*`, `tbl_Hrm_kaizen*` | Out of FSHRMS scope. |
| Access control | `tbl_ANPR_*`, `tbl_visitors_*`, `tbl_vehi_*`, `Visitors_factoryduty*`, `tbl_Canteen_*` | Out of scope; security/canteen are separate systems. |
| Manufacturing | `article_*`, `material_*`, `production_order_*`, `purchase_order_*`, `warehouse_*`, `fit_*`, `Tbl_Inv_*`, `settings_*` | Not HR. |
| Framework | `auth_*`, `authtoken_*`, `django_*`, `Login_loginmodel`, `TaskScheduling_*`, `tbl_Login_Track`, `tbl_log_file`, `TriggerDeleteLog`, `AllowedDeleters`, `SourceTable` | Django + Django-app cruft. |
| Change history / logs | `apis_*_log`, `PayRoll_payroll_*_log`, `Account_*_log`, `Documentum_*log` | Disabled by default in `tables.yml`. The fresh audit-service starts clean unless explicitly opted in per-table. |
| Numeric scratch | `Table_1`..`Table_102`, `tbl`, `tbl_1`, `tbl_2`, person-named tables, monthly snapshot tables, `*_backup`, `*_temp`, `*_Compliance1` paired copies, `tbl_working_hour_reports*` | Junk. |
| Payroll OT details | `Account_overtime_record`, `Account_overtime_calculation`, `PayRoll_overtime_head`, `PayRoll_payroll_overtime_request*` | OT lives in `attendance.overtime_requests` + derived attendance_records OT minutes. Don't re-replicate. |
| `apis_salary` / `apis_salary_ded` / `apis_salary_policy` | All empty. |
| `apis_managementallowances*` / `manageAllEmpAllow` | Map directly into `employee_salary_components` rows by code; no new table. |
| `apis_country` / `apis_company` / `apis_companybranch` | Single-tenant + single-branch — no catalog needed. |
| `apis_skills` (1 row), `apis_appauthority`, `apis_reason`, `apis_addnoticereasons`, `apis_addnoticetype` | Covered by FSHRMS enums + free-text fields, or out of scope. |

---

## 8. Cutover plan (post-ETL)

**Prod can be loaded directly** while it is still pre-launch — there is no
customer data to endanger, and `00_reset.sql` makes the whole chain
re-runnable, so a bad load is corrected by fixing the mapping and running
again rather than by restoring a backup. Run it on UAT first only if you
want the timings ahead of the prod window; the scripts are identical on
both hosts.

Two things that hold regardless of which host you load:

- **`staging.*` must come from the host you're loading.** `replicate.py`
  writes into the same database as the target, and prod Postgres only
  accepts `127.0.0.1` and the pod CIDR — so it runs *on* the prod VM, with
  `.env.db` present and the legacy MSSQL reachable from there.
- **The ESS passwords become real credentials the moment users are
  onboarded**, pre-launch or not. §6.3's export-and-drop is not a
  prod-only ceremony.

Script order — note `000_bootstrap.sql` comes *before* `00_reset.sql`:

```
000_bootstrap.sql   scratch schema + id_map + ess_initial_passwords
00_reset.sql        wipe prior ETL output (safe on a virgin DB)
01 … 06             catalogs → employees → payroll → attendance → leaves → payroll txns
07_ess_provisioning.sql   auth users (slow: bcrypt, ~10 min / 2,500 users)
08_leave_balances.sql     derived balances, per leave-cycle window
```

Pre-flight: services stopped (on k3s, scale the deployments to 0 — the
loader writes to schemas they would otherwise be reading), Flyway already
at head, `replicate.py` run so `staging.*` is populated.

1. Run the scripts in the order above.
2. Sanity queries:
   ```sql
   select count(*) from employee.employees;                   -- should match legacy active employee count
   select count(*) from attendance.attendance_records
    where attendance_date >= date '2026-07-01';               -- should match staging row count
   select sum(amount) from payroll.permanent_advances
    where status = 'DISBURSED';                               -- spot check against legacy total
   ```
   The attendance cutoff is the fiscal-year start pinned in
   `tables.yml` (`defaults.since_date`), not a rolling window — if that
   date moves, move it here too.
3. Run a parallel payroll for one month (compute against migrated data,
   compare net per employee against legacy `payablesalary`). Differences
   > 1 PKR → investigate.
4. Export `migration.ess_initial_passwords` for HR and drop the table
   (07 §6.3). On a throwaway UAT load these don't matter; anywhere users
   will actually log in, they must not sit in the database.
5. Drop the `migration` schema once verified — it carries `id_map`, the
   password table and the leave-cycle helper functions.
6. Cut users over.

**Caches start cold.** The ETL writes rows without emitting
`employee.events`, so `EmployeeDisplayCache` in payroll / attendance / bff
has nothing in it until the events flow. Reads fall through to
employee-service over HTTP in the meantime — correct, just slower — and the
cache fills as records are touched.
