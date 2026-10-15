// Wraps PersonCell for the EmployeeSummary shape returned by the BFF's
// records/punches endpoints. The summary may be null when the upstream
// employee row was deleted/anonymized — fall back to a "#id" placeholder
// so the row still has a stable subject.

import { PersonCell } from '@/components';
import type { EmployeeSummary } from '../bff-api';

export type EmployeeSummaryCellProps = {
  summary: EmployeeSummary | null;
  /** Upstream id from the row (record.employeeId / punch.employeeId). Used
   *  as fallback label when `summary` is null and to build the link target. */
  fallbackEmployeeId: number;
  /** Set false when the caller handles its own row click (e.g. opening a
   *  drawer) — skips the `/people/{id}` Link so the two clicks don't fight. */
  linkToProfile?: boolean;
};

export function EmployeeSummaryCell({
  summary,
  fallbackEmployeeId,
  linkToProfile = true,
}: EmployeeSummaryCellProps) {
  if (!summary) {
    return (
      <PersonCell
        size="compact"
        person={{
          fullName: `#${fallbackEmployeeId}`,
          subtitle: 'Employee not found',
        }}
      />
    );
  }
  // Prefer designation when the wire has it; fall back to department so
  // the cell always pairs a label with the code (avatar · {label · CODE}).
  const primary = summary.designationName ?? summary.departmentName ?? null;
  return (
    <PersonCell
      size="compact"
      to={linkToProfile ? `/people/${summary.id}` : undefined}
      person={{
        fullName: summary.fullName,
        employeeCode: summary.employeeCode,
        designation: primary,
        profilePictureUrl: summary.profilePictureUrl,
      }}
    />
  );
}
