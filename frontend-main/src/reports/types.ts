// Reports-service contract types — derived from `/bff/reports/catalog`
// and the report-jobs lifecycle. Mirrors the shapes documented in
// `docs/reports-frontend-guide.md` (the FE-facing slice of `docs/reports.md`).
//
// The catalog drives everything: each entry tells the FE how to submit
// (method/path/body|query|path params), what permission it needs, and
// whether the call is sync (browser receives the file) or async (poll
// /reports/jobs/{id} → /reports/jobs/{id}/download).

export type ReportService =
  | 'employee'
  | 'attendance'
  | 'leaves'
  | 'payroll'
  /** Payroll account reports — from the accounts registry, not the BFF catalog. */
  | 'accounts';

export type ReportFormat =
  | 'xlsx'
  | 'pdf'
  | 'csv'
  | 'zip'
  | 'json';

export type ReportMode = 'sync' | 'async';

export type ReportMethod = 'GET' | 'POST';

export type ReportParameterLocation = 'query' | 'path' | 'body';

export type ReportParameterType = 'string' | 'int' | 'date' | 'enum';

export interface ReportParameter {
  name: string;
  location: ReportParameterLocation;
  type: ReportParameterType;
  required: boolean;
  /** Populated only for `type: "enum"` — the closed set of allowed values. */
  values: string[] | null;
  /** Optional one-liner shown under the field as a hint. */
  help: string | null;
}

export interface ReportCatalogEntry {
  /** Stable identifier (e.g. `payroll.pay-register`). Used as React keys. */
  id: string;
  service: ReportService;
  title: string;
  description: string;
  format: ReportFormat;
  mode: ReportMode;
  method: ReportMethod;
  /** Gateway-relative submit path. Templated `{name}` slots are filled from
   *  parameters with `location: path`. */
  path: string;
  /** CASL permission the caller has already passed (catalog is server-filtered). */
  permission: string;
  /** True when generated reports should appear in the user's audit feed. */
  userVisibleAudit: boolean;
  parameters: ReportParameter[];
}

export type ReportJobStatus = 'QUEUED' | 'RUNNING' | 'READY' | 'FAILED';

export interface ReportJob {
  id: number;
  /** Server-side report-type discriminator (e.g. `PAY_REGISTER`). */
  type: string;
  status: ReportJobStatus;
  /** Caller userId as a string. */
  requestedBy: string;
  createdAt: string;
  startedAt: string | null;
  completedAt: string | null;
  /** Bytes once READY; null otherwise. */
  outputSize: number | null;
  errorCode: string | null;
  errorMessage: string | null;
}

/** Form values shape passed from the parameter drawer to `runSyncReport` /
 *  `runAsyncReport`. Keyed by `ReportParameter.name`. */
export type ReportParameterValues = Record<string, unknown>;
