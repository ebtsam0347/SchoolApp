import type { StatusPillTone } from '@/components';
import type { FactoryTourStatus, TravelType, VehicleSource } from './types';

export const TRAVEL_TYPE_LABEL: Record<TravelType, string> = {
  LOCAL: 'Local',
  INTERNATIONAL: 'International',
};

export const FACTORY_TOUR_STATUS_LABEL: Record<FactoryTourStatus, string> = {
  DRAFT: 'Draft',
  PENDING_APPROVAL: 'Pending approval',
  APPROVED: 'Approved',
  REJECTED: 'Rejected',
  CANCELLED: 'Cancelled',
};

export const FACTORY_TOUR_STATUS_TONE: Record<FactoryTourStatus, StatusPillTone> = {
  DRAFT: 'neutral',
  PENDING_APPROVAL: 'warning',
  APPROVED: 'success',
  REJECTED: 'error',
  CANCELLED: 'neutral',
};

/** Turns the /travel service's raw wire status (e.g. "TRANSPORT_PENDING")
 * into a readable label ("Transport pending") for display alongside the
 * collapsed tour status. */
export function formatRawStatus(rawStatus: string): string {
  const words = rawStatus.toLowerCase().split('_');
  return words[0].charAt(0).toUpperCase() + words[0].slice(1) + (words.length > 1 ? ' ' + words.slice(1).join(' ') : '');
}

/** Per-name tone for the /travel service's raw wire status, so distinct
 * mid-workflow stages (Transport pending, Submitted, Settlement pending, ...)
 * don't all show up the same color just because they collapse to the same
 * `FactoryTourStatus` bucket. Unlisted raw statuses fall back to the
 * bucket's own tone. */
const RAW_STATUS_TONE: Record<string, StatusPillTone> = {
  DRAFT: 'neutral',
  SUBMITTED: 'info',
  UNDER_REVIEW: 'info',
  TRANSPORT_PENDING: 'warning',
  TRANSPORT_VERIFICATION: 'warning',
  PENDING_APPROVAL: 'warning',
  APPROVED: 'success',
  IN_PROGRESS: 'primary',
  COMPLETED: 'primary',
  SETTLEMENT_PENDING: 'warning',
  SETTLED: 'success',
  REJECTED: 'error',
  CANCELLED: 'neutral',
  CANCELED: 'neutral',
};

export function factoryTourStatusTone(row: {
  status: FactoryTourStatus;
  rawStatus?: string | null;
}): StatusPillTone {
  if (row.rawStatus && RAW_STATUS_TONE[row.rawStatus]) return RAW_STATUS_TONE[row.rawStatus];
  return FACTORY_TOUR_STATUS_TONE[row.status];
}

export const VEHICLE_SOURCE_LABEL: Record<VehicleSource, string> = {
  COMPANY_VEHICLE: 'Company vehicle',
  PERSONAL_VEHICLE: 'Personal vehicle',
  RENTED_VEHICLE: 'Rented vehicle',
  PUBLIC_TRANSPORT: 'Public transport',
};

/**
 * Not collected on the form — derived for display until a real
 * continent/country field (or geo lookup) exists.
 */
export function continentCountryFor(travelType: TravelType): string {
  return travelType === 'INTERNATIONAL' ? 'International' : 'Asia / Pakistan';
}
