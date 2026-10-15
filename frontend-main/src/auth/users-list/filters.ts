// Filter enums + helpers for the /admin/users listing.

export const STATUS_OPTIONS = [
  { label: 'All', value: 'ALL' },
  { label: 'Active', value: 'ACTIVE' },
  { label: 'Locked', value: 'LOCKED' },
  { label: 'Disabled', value: 'DISABLED' },
] as const;

export type StatusFilter = (typeof STATUS_OPTIONS)[number]['value'];

export const LINKED_OPTIONS = [
  { label: 'Any', value: 'ANY' },
  { label: 'Linked', value: 'LINKED' },
  { label: 'Standalone', value: 'STANDALONE' },
] as const;

export type LinkedFilter = (typeof LINKED_OPTIONS)[number]['value'];

export function linkedFilterToParam(v: LinkedFilter): boolean | null {
  if (v === 'LINKED') return true;
  if (v === 'STANDALONE') return false;
  return null;
}
