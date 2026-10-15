// Thin wrapper over the shared PersonCell that maps an EmployeeSummary
// into the PersonCell shape and links to /people/{id}. Used by every
// inbox segment (leaves / overtime / regularization).

import { PersonCell } from '@/components';
import type { EmployeeSummary } from '@/attendance';

export type EmployeeCellProps = {
  employee: EmployeeSummary;
  size?: 'compact' | 'default';
};

export function EmployeeCell({ employee, size = 'compact' }: EmployeeCellProps) {
  // Always render "{label · CODE}". Prefer designation; fall back to
  // department so the secondary line never collapses to bare code.
  const primary = employee.designationName ?? employee.departmentName ?? null;
  return (
    <PersonCell
      size={size}
      to={`/people/${employee.id}`}
      person={{
        fullName: employee.fullName,
        employeeCode: employee.employeeCode,
        designation: primary,
        profilePictureUrl: employee.profilePictureUrl,
      }}
    />
  );
}
