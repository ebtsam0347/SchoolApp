// /people/$id/employment helpers.

import { LOCALE } from '@/config/locale';

const DATE_FMT = new Intl.DateTimeFormat(LOCALE, {
  month: 'short',
  day: 'numeric',
  year: 'numeric',
});

export function formatDate(value: string | undefined | Date): string {
  if (!value) return '—';
  const d = value instanceof Date ? value : new Date(value);
  return DATE_FMT.format(d);
}

export type ChangeKind =
  | 'manager'
  | 'department'
  | 'section'
  | 'designation'
  | 'grade';

export const KIND_LABEL: Record<ChangeKind, string> = {
  manager: 'manager',
  department: 'department',
  section: 'section',
  designation: 'designation',
  grade: 'grade',
};
