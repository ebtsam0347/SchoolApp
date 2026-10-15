// Display maps + search-param guards for the canteen module. Same shape as
// `src/attendance/format.ts` — labels and StatusPill tones live next to the
// enums they describe so routes stay declarative.

import type { StatusPillTone } from '@/components';
import type {
  CanteenEmployeeCategory,
  CanteenEntrySource,
  CanteenMealType,
  CanteenUploadType,
} from './types';

export const MEAL_TYPE_LABEL: Record<CanteenMealType, string> = {
  BREAKFAST: 'Breakfast',
  LUNCH: 'Lunch',
  DINNER: 'Dinner',
  TEA: 'Tea',
};

export const MEAL_TYPE_TONE: Record<CanteenMealType, StatusPillTone> = {
  BREAKFAST: 'warning',
  LUNCH: 'success',
  DINNER: 'info',
  TEA: 'neutral',
};

export const CANTEEN_SOURCE_LABEL: Record<CanteenEntrySource, string> = {
  DEVICE: 'Device',
  MANUAL: 'Manual',
  BARCODE_UPLOAD: 'Barcode file',
  MANUAL_CARD_UPLOAD: 'Manual-card file',
};

export const CANTEEN_SOURCE_TONE: Record<CanteenEntrySource, StatusPillTone> = {
  DEVICE: 'success',
  MANUAL: 'warning',
  BARCODE_UPLOAD: 'info',
  MANUAL_CARD_UPLOAD: 'info',
};

export const UPLOAD_TYPE_LABEL: Record<CanteenUploadType, string> = {
  BARCODE: 'Barcode',
  MANUAL_CARD: 'Manual card',
};

export const CANTEEN_CATEGORY_LABEL: Record<CanteenEmployeeCategory, string> = {
  LABOUR: 'Labour',
  BOUFET: 'Boufet',
};

export const CANTEEN_CATEGORY_TONE: Record<CanteenEmployeeCategory, StatusPillTone> = {
  LABOUR: 'info',
  BOUFET: 'success',
};

const MEAL_TYPES: readonly CanteenMealType[] = ['BREAKFAST', 'LUNCH', 'DINNER', 'TEA'];

export function isCanteenMealType(v: unknown): v is CanteenMealType {
  return typeof v === 'string' && (MEAL_TYPES as readonly string[]).includes(v);
}

const SOURCES: readonly CanteenEntrySource[] = [
  'DEVICE',
  'MANUAL',
  'BARCODE_UPLOAD',
  'MANUAL_CARD_UPLOAD',
];

export function isCanteenEntrySource(v: unknown): v is CanteenEntrySource {
  return typeof v === 'string' && (SOURCES as readonly string[]).includes(v);
}

const CATEGORIES: readonly CanteenEmployeeCategory[] = ['LABOUR', 'BOUFET'];

export function isCanteenCategory(v: unknown): v is CanteenEmployeeCategory {
  return typeof v === 'string' && (CATEGORIES as readonly string[]).includes(v);
}

/** "13:45:00" / "13:45" → "13:45". Empty input renders as an em dash. */
export function formatCanteenTime(time: string | null | undefined): string {
  if (!time) return '—';
  return time.slice(0, 5);
}
