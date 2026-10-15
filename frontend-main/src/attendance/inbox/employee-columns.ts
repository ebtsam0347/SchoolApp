// Employee-summary helpers shared by the approvals-inbox tables.

import type { EmployeeSummary } from '@/attendance';

/** `CODE · Department` subtitle for the Employee person cell. */
export function employeeSubtitle(e: EmployeeSummary): string | undefined {
  return [e.employeeCode, e.departmentName].filter(Boolean).join(' · ') || undefined;
}
