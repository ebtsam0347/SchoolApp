// Display helpers used only on /time/today.

export { daysUntil } from '@/lib/dates';

export function prettyHolidayType(t: string): string {
  return t.charAt(0) + t.slice(1).toLowerCase();
}
