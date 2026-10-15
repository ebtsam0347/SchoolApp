// Helpers for keeping the React-Query cache for /bff/employee/{id} in sync
// with mutations that go directly to employee-service.
//
// Why: writes hit the gateway and land on the upstream service. The detail
// page then refetches /bff/employee/{id}, but the BFF composite has its
// own cache busted by Kafka events — the event lands milliseconds after
// the write, so a refetch fired immediately after the mutation often
// returns stale data. We bridge that race by patching the cached
// EmployeeDetail with the mutation response (and any catalog labels the
// caller can hand us) before the background refetch reconciles.
//
// Always pair `patchEmployeeDetail` with `qc.invalidateQueries` so the
// eventual BFF refetch overwrites our optimistic guess. If the BFF
// returns the same data, the user sees no flicker. If it differs (e.g.
// derived fields like activeProbation), the refetch wins — patching
// only sets fields we have first-hand info about.

import type { QueryClient } from '@tanstack/react-query';
import type {
  CityDto,
  DepartmentDto,
  DesignationDto,
  EmployeeDetail,
  EmployeeDto,
  ExitDto,
  GradeDto,
  SectionDto,
  SuspensionDto,
} from './types';

export function detailQueryKey(employeeId: number) {
  return ['people', 'detail', employeeId] as const;
}

/** Common invalidation set for any mutation that touches an employee. */
export function invalidateEmployeeReads(qc: QueryClient, employeeId: number) {
  void qc.invalidateQueries({ queryKey: detailQueryKey(employeeId) });
  void qc.invalidateQueries({ queryKey: ['people', 'bff-history', employeeId] });
  void qc.invalidateQueries({ queryKey: ['people', 'timeline', employeeId] });
  void qc.invalidateQueries({ queryKey: ['people', 'directory'] });
  void qc.invalidateQueries({ queryKey: ['people', 'stats'] });
}

type ResolvedPatch = {
  department?: DepartmentDto | null;
  section?: SectionDto | null;
  designation?: DesignationDto | null;
  grade?: GradeDto | null;
  city?: CityDto | null;
  manager?:
    | {
        id?: number;
        fullName?: string;
        employeeCode?: string;
        profilePictureUrl?: string;
      }
    | null;
};

/**
 * Apply an optimistic patch to the cached EmployeeDetail. Each field is
 * applied independently — only patch what you actually know.
 *
 * `null` clears a resolved field (e.g. clear `section` when a department
 * change invalidates the previous section).
 *
 * `undefined` is a no-op for that field.
 */
export function patchEmployeeDetail(
  qc: QueryClient,
  employeeId: number,
  patch: {
    employee?: Partial<EmployeeDto>;
    resolved?: ResolvedPatch;
    activeSuspension?: SuspensionDto | null;
    exit?: ExitDto | null;
  },
) {
  qc.setQueryData<EmployeeDetail>(detailQueryKey(employeeId), (old) => {
    if (!old) return old;
    const next: EmployeeDetail = { ...old };
    if (patch.employee) {
      next.employee = { ...(old.employee ?? {}), ...patch.employee };
    }
    if (patch.resolved) {
      const r: NonNullable<EmployeeDetail['resolved']> = { ...(old.resolved ?? {}) };
      for (const key of Object.keys(patch.resolved) as (keyof ResolvedPatch)[]) {
        const val = patch.resolved[key];
        if (val === undefined) continue; // no-op
        if (val === null) {
          (r as Record<string, unknown>)[key] = undefined;
        } else {
          (r as Record<string, unknown>)[key] = val;
        }
      }
      next.resolved = r;
    }
    if (patch.activeSuspension !== undefined) {
      next.activeSuspension = patch.activeSuspension ?? undefined;
    }
    if (patch.exit !== undefined) {
      next.exit = patch.exit ?? undefined;
    }
    return next;
  });
}
