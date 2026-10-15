import { EMPLOYMENT_TYPE_LABEL } from '@/people';
import type { StatusPillTone } from '@/components';
import type { RequisitionStatus, RequisitionType } from './types';

export { EMPLOYMENT_TYPE_LABEL };

export const REQUISITION_TYPE_LABEL: Record<RequisitionType, string> = {
  NEW_HIRE: 'New hire',
  REPLACEMENT: 'Replacement',
};

export const REQUISITION_TYPE_COLOR: Record<RequisitionType, string> = {
  NEW_HIRE: 'blue',
  REPLACEMENT: 'purple',
};

export const REQUISITION_STATUS_LABEL: Record<RequisitionStatus, string> = {
  DRAFT: 'Draft',
  PENDING_APPROVAL: 'Pending approval',
  APPROVED: 'Approved',
  REJECTED: 'Rejected',
  FULFILLED: 'Fulfilled',
  CANCELLED: 'Cancelled',
};

export const REQUISITION_STATUS_TONE: Record<RequisitionStatus, StatusPillTone> = {
  DRAFT: 'neutral',
  PENDING_APPROVAL: 'warning',
  APPROVED: 'success',
  REJECTED: 'error',
  FULFILLED: 'primary',
  CANCELLED: 'neutral',
};