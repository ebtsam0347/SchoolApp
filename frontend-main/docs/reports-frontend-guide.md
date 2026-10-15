# Reports — Frontend integration guide

Minimal wiring guide for the FE team. Everything routes through the
gateway at `http://localhost:8080`. JWT in `Authorization: Bearer <token>`.

For the full design, see `docs/reports.md`. This doc just lists the
endpoints, their parameters, and the patterns the FE needs to know.

## 1. Login + token

```
POST /auth/login
Content-Type: application/json
{ "username": "...", "password": "..." }

→ 200 OK
{ "accessToken": "<jwt>", "refreshToken": "...", "expiresInSeconds": 900 }
```

Send `Authorization: Bearer <accessToken>` on every report call. The token
carries the caller's roles + permissions; the gateway validates, the
backend services trust the injected `X-User-*` headers.

## 2. Reports catalogue (one call drives the catalogue UI)

```
GET /bff/reports/catalog
→ 200 OK
[
  {
    "id": "payroll.pay-register",
    "service": "payroll",
    "title": "Pay register (per run)",
    "description": "Async — every payslip × every component, footed.",
    "format": "xlsx",
    "mode": "async",
    "method": "POST",
    "path": "/reports/payroll/pay-register/jobs",
    "permission": "payroll.reports.run",
    "userVisibleAudit": false,
    "parameters": [
      { "name": "payrollRunId", "location": "body", "type": "int", "required": true, "values": null, "help": "Payroll run id." }
    ]
  },
  ...
]
```

The list is **already filtered** by what the caller can run. Render one
card per entry. Build the parameter form from `parameters[]` (each entry
has `location ∈ {query,path,body}` and `type ∈ {string,int,date,enum}`).
Then submit to `path` exactly as listed.

## 3. Sync vs async

- `mode: "sync"` → `GET path?…`. Browser receives the file directly.
  Send the JWT. Pull `Content-Disposition` for the suggested filename.
- `mode: "async"` → `POST path?…` (or with a JSON body if `parameters`
  has `location: body`). Response is a `ReportJob` row:

  ```json
  { "id": 7, "type": "PAY_REGISTER", "status": "QUEUED", "requestedBy": "158",
    "createdAt": "2026-05-09T04:25:27.65", "startedAt": null, "completedAt": null,
    "outputSize": null, "errorCode": null, "errorMessage": null }
  ```

  Poll every 2-3 seconds:

  ```
  GET /reports/jobs/{id}
  → 200 { ..., "status": "READY", "outputSize": 8604 }    ← READY: download next
  → 200 { ..., "status": "FAILED", "errorMessage": "..." }
  → 200 { ..., "status": "QUEUED" | "RUNNING" }           ← keep polling
  ```

  Once READY:

  ```
  GET /reports/jobs/{id}/download
  → 302 Location: <signed-MinIO-URL>
  ```

  Browsers follow the 302 automatically (`<a href>` works). For
  programmatic clients in local dev, MinIO signs URLs as
  `http://minio:9000/...`; resolve `minio` → `127.0.0.1` (curl
  `--resolve minio:9000:127.0.0.1`, or add `127.0.0.1 minio` to
  `/etc/hosts`). UAT/prod will sign against the public hostname.

## 4. Endpoint reference

Path is the gateway URL (prepend `http://localhost:8080`). All require
the listed permission.

### Employee reports — `employee.reports.run`

| Method | Path | Format | Notes |
|---|---|---|---|
| GET | `/reports/employees/directory` | CSV | All non-terminated employees with contact + assignment columns. Manager scope auto-applied. |
| GET | `/reports/employees/headcount-snapshot?asOf=` | PDF | Headcount by dept + grade as of a date. `asOf` optional. |
| GET | `/reports/employees/joiners?from=&to=` | XLSX | Joining-date in range. |
| GET | `/reports/employees/leavers?from=&to=` | XLSX | Completed exits with last-working-day in range. |
| GET | `/reports/employees/contract-expiries?days=` | XLSX | Active contracts ending within N days. |
| GET | `/reports/employees/document-expiries?days=` | XLSX | CNIC + certifications expiring within N days. |
| GET | `/reports/employees/confirmations-due?days=` | XLSX | Active probation contracts ending within N days. |
| GET | `/reports/employees/{id}/service-letter` | PDF | Current employee. |
| GET | `/reports/employees/{id}/experience-letter` | PDF | Terminated employee only. |

### Attendance reports — `attendance.reports.run`

| Method | Path | Format | Notes |
|---|---|---|---|
| GET | `/reports/attendance/daily?date=` | XLSX | One date, every employee. |
| GET | `/reports/attendance/monthly?year=&month=` | XLSX | Per-employee status counts for the month. |
| GET | `/reports/attendance/late-comers?from=&to=` | XLSX | Aggregated late counts ranked by frequency. |
| GET | `/reports/attendance/absentee-log?from=&to=` | XLSX | ABSENT records only. |
| GET | `/reports/attendance/ot-register?from=&to=` | XLSX | OT requests + computed minutes. |
| GET | `/reports/attendance/regularization-log?from=&to=&status=` | XLSX | Optional status (`SUBMITTED`/`APPROVED`/`REJECTED`/`WITHDRAWN`). |
| GET | `/reports/attendance/missing-punch?from=&to=` | XLSX | PRESENT records with at least one missing punch. |
| GET | `/reports/attendance/shift-roster?from=&to=` | XLSX | Active shift assignments overlapping the range. |

### Leave reports — `leaves.reports.run`

| Method | Path | Format | Notes |
|---|---|---|---|
| GET | `/reports/leaves/balance?year=` | XLSX | Per-employee × leave-type allocated/used/pending/carried. |
| GET | `/reports/leaves/taken?from=&to=` | XLSX | Approved leave days in the range. |
| GET | `/reports/leaves/application-log?from=&to=` | XLSX | Every leave request + status in the range. |
| GET | `/reports/leaves/carry-forward?year=` | XLSX | Projected carry into next year. |

### Payroll reports — `payroll.reports.run`

| Method | Path | Format | Mode | Notes |
|---|---|---|---|---|
| GET | `/reports/payroll/payslips/{id}/pdf-url` | JSON `{ "url": "..." }` | sync | Returns a signed MinIO URL for an already-generated payslip PDF. 404 when the run-finalize batch hasn't run. |
| GET | `/reports/payroll/eobi-return?year=&month=` | PDF | sync | Statutory layout. EOBI-enrolled employees with non-zero contribution. |
| GET | `/reports/payroll/pessi-return?year=&month=` | PDF | sync | Statutory layout. Employer-only. |
| GET | `/reports/payroll/income-tax-deducted?fromYear=&fromMonth=&toYear=&toMonth=` | XLSX | sync | Period total + fiscal-year cumulative. Bounds must lie in the same FY. |
| GET | `/reports/payroll/variance?currentRunId=&priorRunId=` | XLSX | sync | `priorRunId` optional; default = same-FY immediate predecessor. 409 when no prior run. |
| GET | `/reports/payroll/advances-outstanding` | XLSX | sync | Two sheets — permanent + short-term. |
| GET | `/reports/payroll/off-cycle-payments?from=&to=&category=` | XLSX | sync | Optional `category` (`BONUS`/`INCREMENT`/`ADJUSTMENT`/`ENCASHMENT`). |
| POST | `/reports/payroll/pay-register/jobs` | XLSX | async | Body: `{ "payrollRunId": 1 }`. Then poll `/reports/jobs/{id}`. |
| POST | `/reports/payroll/increment-letters/jobs?incrementBatchId=` | ZIP | async | One PDF per employee, zipped. |
| POST | `/reports/payroll/payslips-run/jobs?payrollRunId=` | ZIP | async | Zips already-generated payslip PDFs. Fails fast if none exist on the run. |

### Async-job lifecycle

| Method | Path | Notes |
|---|---|---|
| GET | `/reports/jobs/{id}` | Status row. |
| GET | `/reports/jobs/{id}/download` | 302 → signed URL (READY only; 409 otherwise). |

## 5. Error shape

Every error follows RFC 7807 ProblemDetail:

```json
{
  "title": "Validation failed",
  "code": "VALIDATION_FAILED",
  "status": 422,
  "detail": "from must be ≤ to",
  "instance": "/reports/payroll/off-cycle-payments",
  "correlationId": "01KR4Y...",
  "timestamp": "2026-05-08T23:42:44.984Z"
}
```

Common codes:
- `401` no/expired token; refresh + retry.
- `403` caller lacks the `<svc>.reports.run` permission.
- `404` referenced resource (run, employee, batch) doesn't exist.
- `409` validation conflict (range inverted, no prior run, period not in FY).
- `422` value-shape validation (negative `days`, missing required field).

## 6. Putting it together — a minimal sync example

```ts
async function downloadReport(path: string, token: string) {
  const resp = await fetch(`http://localhost:8080${path}`, {
    headers: { Authorization: `Bearer ${token}` }
  });
  if (!resp.ok) throw await resp.json();
  const blob = await resp.blob();
  const cd = resp.headers.get('Content-Disposition') ?? '';
  const filename = /filename="([^"]+)"/.exec(cd)?.[1] ?? 'report';
  const url = URL.createObjectURL(blob);
  const a = Object.assign(document.createElement('a'), { href: url, download: filename });
  a.click();
  URL.revokeObjectURL(url);
}
```

## 7. Putting it together — a minimal async example

```ts
async function runAsyncReport(jobPath: string, params: object, token: string) {
  // 1. enqueue
  const enqueue = await fetch(`http://localhost:8080${jobPath}`, {
    method: 'POST',
    headers: { Authorization: `Bearer ${token}`, 'Content-Type': 'application/json' },
    body: JSON.stringify(params)
  });
  const job = await enqueue.json();
  const id = job.id;

  // 2. poll
  while (true) {
    await new Promise(r => setTimeout(r, 2000));
    const status = await fetch(`http://localhost:8080/reports/jobs/${id}`,
      { headers: { Authorization: `Bearer ${token}` } }).then(r => r.json());
    if (status.status === 'READY')  break;
    if (status.status === 'FAILED') throw new Error(status.errorMessage);
  }

  // 3. download — the 302 lands in window.location for a real <a> click,
  //    or follow it manually here.
  window.location.href = `http://localhost:8080/reports/jobs/${id}/download`;
}
```

## 8. Analytics endpoints

Powering the `/analytics` dashboards. All return JSON; the FE charts
them client-side. Same gateway base URL + JWT as the report endpoints.

### Permissions

| Permission | Granted to |
|---|---|
| `employee.analytics.read`   | ADMIN, HR, MANAGER |
| `attendance.analytics.read` | ADMIN, HR, MANAGER |
| `leaves.analytics.read`     | ADMIN, HR, MANAGER |
| `payroll.analytics.read`    | ADMIN, HR, MANAGER |

### People analytics — `employee.analytics.read`

| Method | Path | Notes |
|---|---|---|
| GET | `/analytics/employees/headcount-trend?months=` | Monthly joiners / leavers / endHeadcount over a window (1..24, default 12). |
| GET | `/analytics/employees/attrition?months=` | Per-month leavers + start/end headcount + ratePct. Plus `rollingTwelveMonthPct` summary. |
| GET | `/analytics/employees/tenure-bands?splitBy=dept|grade` | Active employees bucketed into <1y / 1–3y / 3–5y / 5–10y / 10y+. Optional split. |

Sample (`/analytics/employees/attrition?months=4`):

```json
{
  "months": 4,
  "series": [
    { "month": "2026-02", "leavers": 37, "startHeadcount": 5849, "endHeadcount": 5853, "ratePct": 0.63 },
    ...
  ],
  "rollingTwelveMonthPct": 10.35
}
```

### Attendance analytics — `attendance.analytics.read`

| Method | Path | Notes |
|---|---|---|
| GET | `/analytics/attendance/on-time-pct?months=`     | Monthly on-time%. `series[i] = { month, denominator (PRESENT), numerator (on-time), pct }`. |
| GET | `/analytics/attendance/absenteeism-pct?months=` | Monthly absenteeism%. Same shape. |
| GET | `/analytics/attendance/ot-trend?months=`        | Monthly OT minutes + hours. |
| GET | `/analytics/attendance/scorecard/{employeeId}?days=` | Rolling N-day scorecard for one employee — present/late/absent counts + on-time% + OT hours. |

### Leave analytics — `leaves.analytics.read`

| Method | Path | Notes |
|---|---|---|
| GET | `/analytics/leaves/utilization?year=`     | Per leave-type allocated/used/pending + used%. Plus company-wide overall%. |
| GET | `/analytics/leaves/seasonality?months=`   | Monthly leave days taken with per-leave-type breakdown. |

### Payroll analytics — `payroll.analytics.read`

| Method | Path | Notes |
|---|---|---|
| GET | `/analytics/payroll/cost-trend?months=`         | Per month: gross / EOBI / PESSI / tax / net + employer totalCost + costPerHead. |
| GET | `/analytics/payroll/salary-distribution`        | Min/p25/median/p75/max of current basic across all employees + raw sorted basics when ≤5000. |

### Cross-service composite — `/bff` (any authenticated caller)

| Method | Path | Notes |
|---|---|---|
| GET | `/bff/analytics/executive-overview`             | Single payload combining the latest values from all four service-side analytics endpoints. Each field falls back to `null` when its downstream is unreachable. |

Sample:

```json
{
  "headcount": 5722,
  "attritionRolling12mPct": 14.80,
  "latestMonthOnTimePct": 99.91,
  "latestMonthAbsenteeismPct": 0.00,
  "latestMonthOtHours": 0.00,
  "latestMonthPayrollCost": 0.00
}
```

## 9. Out of scope (here)

- Per-dept splits on the analytics endpoints — today they're company-wide
  except for `headcount-trend` (which carries headcount, not dept). Add
  `?splitBy=dept` to each when the FE needs per-dept dashboards.
- PDF executive versions of the dashboards (phase 7b in `docs/reports.md`)
  — wait until the FE is settled before locking layout in Jasper.
- Scheduled / emailed reports — not built; HR pulls on demand.
- A generic ad-hoc report builder UI — reports stay coded.
