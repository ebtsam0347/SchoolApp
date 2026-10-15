import type { StatusPillTone } from '@/components';
import type { ActivationStatus, ClearanceStatus } from './types';

export const ACTIVATION_STATUS_LABEL: Record<ActivationStatus, string> = {
  ACTIVE: 'Active',
  INACTIVE: 'Inactive',
};

export const ACTIVATION_STATUS_TONE: Record<ActivationStatus, StatusPillTone> = {
  ACTIVE: 'success',
  INACTIVE: 'neutral',
};

export const CLEARANCE_STATUS_LABEL: Record<ClearanceStatus, string> = {
  PENDING: 'Pending',
  APPROVED: 'Cleared',
  REJECTED: 'Rejected',
};

export const CLEARANCE_STATUS_TONE: Record<ClearanceStatus, StatusPillTone> = {
  PENDING: 'warning',
  APPROVED: 'success',
  REJECTED: 'error',
};
