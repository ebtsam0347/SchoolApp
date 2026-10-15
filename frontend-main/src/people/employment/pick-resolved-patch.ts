// Build the resolved-labels patch for an optimistic detail update after
// a change-* mutation. Each kind reaches into the catalog the modal
// already loaded; for `manager` we use the typeahead's last result set.
// Returns `undefined` when we can't resolve the new label — the BFF
// refetch will fix it eventually.

import type {
  DepartmentDto,
  DesignationDto,
  EmployeeDto,
  GradeDto,
  SectionDto,
} from '@/people';
import type { ChangeKind } from './format';

export function pickResolvedPatch(
  kind: ChangeKind,
  targetId: number | undefined,
  catalogs: {
    departments?: DepartmentDto[];
    sections?: SectionDto[];
    designations?: DesignationDto[];
    grades?: GradeDto[];
    managers?: EmployeeDto[];
  },
) {
  if (!targetId) return {};
  if (kind === 'department') {
    const dep = catalogs.departments?.find((d) => d.id === targetId);
    // Section may now reference a different department — clear it so
    // the strip doesn't carry a stale label until the BFF refetch fixes it.
    return dep ? { department: dep, section: null } : { section: null };
  }
  if (kind === 'section') {
    const sec = catalogs.sections?.find((s) => s.id === targetId);
    return sec ? { section: sec } : {};
  }
  if (kind === 'designation') {
    const d = catalogs.designations?.find((d) => d.id === targetId);
    return d ? { designation: d } : {};
  }
  if (kind === 'grade') {
    const g = catalogs.grades?.find((g) => g.id === targetId);
    return g ? { grade: g } : {};
  }
  // manager
  const m = catalogs.managers?.find((m) => m.id === targetId);
  if (!m?.id) return {};
  return {
    manager: {
      id: m.id,
      fullName: m.fullName,
      employeeCode: m.employeeCode,
      profilePictureUrl: m.profilePictureUrl,
    },
  };
}
