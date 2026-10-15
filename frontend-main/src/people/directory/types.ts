import type { EmploymentStatus, EmploymentType } from '../types';

export type DirectoryView = 'table' | 'card';

export type DirectorySearch = {
  q?: string;
  status?: EmploymentStatus;
  employmentType?: EmploymentType;
  departmentId?: number;
  sectionId?: number;
  designationId?: number;
  gradeId?: number;
  hasEss?: boolean;
  page?: number;
  size?: number;
  sort?: string;
  view?: DirectoryView;
};

export const STATUSES: EmploymentStatus[] = [
  'ACTIVE',
  'ON_NOTICE',
  'ON_LEAVE',
  'SUSPENDED',
  'TERMINATED',
];

export const TYPES: EmploymentType[] = ['PERMANENT', 'CONTRACT', 'INTERN'];

export function isStatus(v: unknown): v is EmploymentStatus {
  return typeof v === 'string' && (STATUSES as string[]).includes(v);
}

export function isType(v: unknown): v is EmploymentType {
  return typeof v === 'string' && (TYPES as string[]).includes(v);
}

export function isView(v: unknown): v is DirectoryView {
  return v === 'table' || v === 'card';
}

export function numOrUndef(v: unknown): number | undefined {
  if (v === undefined || v === null || v === '') return undefined;
  const n = Number(v);
  return Number.isFinite(n) ? n : undefined;
}
