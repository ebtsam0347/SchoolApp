import type { Dayjs } from 'dayjs';

export type VisibilityFilter = 'ALL' | 'USER_VISIBLE' | 'INTERNAL';

export const VISIBILITY_OPTIONS: { label: string; value: VisibilityFilter }[] = [
  { label: 'All visibility', value: 'ALL' },
  { label: 'User-visible only', value: 'USER_VISIBLE' },
  { label: 'Internal only', value: 'INTERNAL' },
];

export function visibilityToParam(v: VisibilityFilter): boolean | undefined {
  if (v === 'USER_VISIBLE') return true;
  if (v === 'INTERNAL') return false;
  return undefined;
}

export type FiltersState = {
  q: string;
  entityType: string | undefined;
  action: string | undefined;
  sourceService: string | undefined;
  visibility: VisibilityFilter;
  range: [Dayjs, Dayjs] | null;
};
