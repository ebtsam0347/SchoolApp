// Account Reports — payroll-service via the gateway. Needs
// `payroll.reports.run`.
//
//   GET  /reports/payroll/accounts/registry          every implemented report
//   POST /reports/payroll/accounts/{id}/data         one page of rows (JSON)
//   POST /reports/payroll/accounts/{id}/export       xlsx | csv file
//   POST /reports/payroll/accounts/{id}/document     PDF (SLIP / LETTER / LEDGER)
//
// All three POSTs take the same filter body. File calls go through
// `fetchReportFile`, which parses a failed blob response's ProblemDetail
// into the usual `ApiError` and reads the `Content-Disposition` filename.

import { http } from '@/lib/http';
import { toIsoDate } from '@/lib';
import { qs } from '@/lib/qs';

import { fetchReportFile, type ReportFile } from '../download';
import type { SlipLayout } from '../slip-layout';
import type {
  AccountsReportDefinition,
  AccountsReportFilterBody,
  AccountsReportResult,
  SlipLanguage,
} from './types';

const BASE = '/reports/payroll/accounts';

const path = (reportId: string, action: 'data' | 'export' | 'document') =>
  `${BASE}/${encodeURIComponent(reportId)}/${action}`;

/** Used only when the server sends no `Content-Disposition`. */
const fallbackName = (reportId: string, ext: string) =>
  `${reportId}-${toIsoDate(new Date())}.${ext}`;

export type ExportFormat = 'xlsx' | 'csv';

export const accountsReportsApi = {
  registry: (signal?: AbortSignal) =>
    http.get<AccountsReportDefinition[]>(`${BASE}/registry`, { signal }),

  data: (reportId: string, body: AccountsReportFilterBody, signal?: AbortSignal) =>
    http.post<AccountsReportResult>(path(reportId, 'data'), body, { signal }),

  export: (
    reportId: string,
    format: ExportFormat,
    body: AccountsReportFilterBody,
  ): Promise<ReportFile> =>
    fetchReportFile(`${path(reportId, 'export')}${qs({ format })}`, fallbackName(reportId, format), {
      body,
    }),

  document: (
    reportId: string,
    body: AccountsReportFilterBody,
    language: SlipLanguage | undefined,
    /** A1 only — other documents ignore it. */
    layout?: SlipLayout,
    signal?: AbortSignal,
  ): Promise<ReportFile> =>
    fetchReportFile(`${path(reportId, 'document')}${qs({ language, layout })}`, fallbackName(reportId, 'pdf'), {
      body,
      signal,
      accept: 'application/pdf',
    }),
};
