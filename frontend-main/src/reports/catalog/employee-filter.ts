// Detection + encoding for the shared employee filter panel
// (EmployeeFilterPanel.tsx), and the parameter-name → lookup map used by
// the generic renderer's `int *Id` rule.

import type { ReportCatalogEntry } from '../types';

export const EMPLOYEE_FILTER_PARAMS = [
  'status',
  'departmentId',
  'sectionId',
  'designationId',
  'cardFrom',
  'cardTo',
  'cards',
] as const;

export type EmployeeFilterParam = (typeof EMPLOYEE_FILTER_PARAMS)[number];

/** A report gets the panel when it declares `status` + `departmentId`. */
export function hasEmployeeFilter(entry: ReportCatalogEntry): boolean {
  const names = new Set(entry.parameters.map((p) => p.name));
  return names.has('status') && names.has('departmentId');
}

/** Panel form values → catalog parameter values (in place). */
export function encodeEmployeeFilter(out: Record<string, unknown>): void {
  if (Array.isArray(out.cards)) {
    out.cards = (out.cards as unknown[])
      .map((c) => String(c).trim())
      .filter(Boolean)
      .join(',');
  }
  for (const k of ['cardFrom', 'cardTo'] as const) {
    if (typeof out[k] === 'string') out[k] = (out[k] as string).trim();
  }
}

export type LookupKind = 'department' | 'section' | 'designation' | 'grade';

/** Parameter name → lookup, for the `int *Id` rule of the generic renderer. */
export const LOOKUP_PARAMS: Record<string, LookupKind> = {
  departmentId: 'department',
  sectionId: 'section',
  designationId: 'designation',
  gradeId: 'grade',
};
