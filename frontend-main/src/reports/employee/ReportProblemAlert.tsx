// 422 rendering for the per-employee letters and certificates: the server's
// `detail` in an Alert, plus a link to the tab where the blocking data is
// fixed. Anything that isn't a recognised 422 returns `undefined`, so
// PdfPreviewDrawer falls back to its generic alert.

import type { ReactNode } from 'react';
import { Alert } from 'antd';
import { Link } from '@tanstack/react-router';

import { isApiError } from '@/lib';

export type PerEmployeeReport =
  | 'confirmationLetter'
  | 'relievingLetter'
  | 'characterCertificate'
  | 'salaryCertificate';

/** A ProblemDetail extension, whether sent flat or under `extensions`. */
export function problemExtension(err: unknown, key: string): unknown {
  if (!isApiError(err)) return undefined;
  const p = err.problem as Record<string, unknown>;
  if (p[key] !== undefined) return p[key];
  const ext = p.extensions;
  return ext && typeof ext === 'object'
    ? (ext as Record<string, unknown>)[key]
    : undefined;
}

type TabLink = {
  to: '/people/$id/employment' | '/people/$id/salary';
  hash?: string;
  label: string;
};

function tabLinkFor(report: PerEmployeeReport, err: unknown): TabLink | null {
  if (!isApiError(err)) return null;
  switch (report) {
    case 'confirmationLetter': {
      const missing = problemExtension(err, 'missingFields');
      return Array.isArray(missing) && missing.includes('confirmationDate')
        ? { to: '/people/$id/employment', label: 'Set the confirmation date on the Employment tab' }
        : null;
    }
    case 'characterCertificate':
      return { to: '/people/$id/employment', hash: 'warnings', label: 'View disciplinary record' };
    case 'salaryCertificate':
      return err.code === 'NO_BASIC_SALARY'
        ? { to: '/people/$id/salary', label: 'Set the basic salary on the Salary tab' }
        : null;
    case 'relievingLetter':
      return null;
  }
}

function titleFor(report: PerEmployeeReport, err: unknown): string {
  if (report === 'characterCertificate') {
    const actions = problemExtension(err, 'disciplinaryActions');
    if (Array.isArray(actions) && actions.length > 0) return 'Disciplinary record on file';
  }
  if (report === 'salaryCertificate' && isApiError(err) && err.code === 'EMPLOYEE_INACTIVE') {
    return 'Employee is not active';
  }
  return "This document can't be issued yet";
}

export function reportProblemAlert(
  report: PerEmployeeReport,
  err: unknown,
  employeeId: number,
  onNavigate?: () => void,
): ReactNode | undefined {
  if (!isApiError(err) || err.status !== 422) return undefined;
  const link = tabLinkFor(report, err);
  return (
    <Alert
      type="warning"
      showIcon
      title={titleFor(report, err)}
      description={
        <>
          {err.problem.detail ?? err.problem.title}
          {link ? (
            <div style={{ marginTop: 6 }}>
              <Link
                to={link.to}
                params={{ id: String(employeeId) }}
                hash={link.hash}
                onClick={onNavigate}
              >
                {link.label}
              </Link>
            </div>
          ) : null}
        </>
      }
    />
  );
}
