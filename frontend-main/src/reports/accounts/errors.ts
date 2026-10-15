// User-facing copy for Account Reports failures. The same mapping serves
// the JSON data call and the blob export / document calls (whose error
// bodies `fetchReportFile` has already parsed into an `ApiError`).

import { getErrorMessage, isApiError } from '@/lib';

export type AccountsErrorCopy = {
  title: string;
  /** Extra line under the title, when there's something more to say. */
  detail?: string;
  /** Whether trying again could help (drives the Retry button). */
  retryable: boolean;
};

export function describeAccountsError(err: unknown): AccountsErrorCopy {
  if (!isApiError(err)) {
    return { title: "Couldn't run the report", detail: getErrorMessage(err), retryable: true };
  }
  if (err.status === 403) {
    return { title: "You don't have access to Account Reports", retryable: false };
  }
  switch (err.code) {
    case 'ACCOUNTS_REPORT_FILTER_INVALID': {
      const lines = err.violations.map((v) => v.message);
      return {
        title: 'Some filters need attention',
        detail: lines.length ? lines.join(' · ') : err.problem.detail,
        retryable: false,
      };
    }
    case 'ACCOUNTS_REPORT_NOT_FOUND':
      return { title: 'Report not available', retryable: false };
    case 'ACCOUNTS_REPORT_NOTHING_TO_PRINT':
      return err.status === 422
        ? { title: 'Select exactly one card for the ledger', retryable: false }
        : { title: 'No employees match these filters — nothing to print', retryable: false };
    case 'ACCOUNTS_REPORT_BANK_ACCOUNT_MISSING':
      return {
        title: 'Company account is not configured for this bank. Set it in Payroll → Banks.',
        retryable: false,
      };
    case 'ACCOUNTS_REPORT_EMPLOYEE_LOOKUP_FAILED':
      return {
        title: 'Employee data is unavailable — please try again',
        detail: `Reference: ${err.problem.correlationId}`,
        retryable: true,
      };
    case 'ACCOUNTS_REPORT_NOT_A_DOCUMENT':
      return { title: 'This report has no printable document', retryable: false };
    default:
      if (err.status === 404) {
        return { title: 'Nothing to print for these filters', detail: err.problem.detail, retryable: false };
      }
      return { title: "Couldn't run the report", detail: getErrorMessage(err), retryable: true };
  }
}

/** One-line toast text for a failed download / print. */
export function accountsErrorToast(err: unknown): string {
  const c = describeAccountsError(err);
  return c.detail ? `${c.title}: ${c.detail}` : c.title;
}
