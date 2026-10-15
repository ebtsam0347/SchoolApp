// Display helpers for Salary Component Transactions. Status labels + tones
// mirror the payroll-run pattern for consistency.

import type { StatusPillTone } from '@/components';
import type {
  SalaryComponentTransactionStatus,
  TransactionLineStatus,
} from './types';

export const TRANSACTION_STATUS_LABEL: Record<SalaryComponentTransactionStatus, string> = {
  DRAFT: 'Draft',
  LOCKED: 'Attendance locked',
  COMPUTING: 'Computing',
  COMPUTED: 'Computed',
  APPROVED: 'Approved',
  POSTING: 'Posting',
  POSTED: 'Posted',
  FAILED: 'Failed',
  CANCELLED: 'Cancelled',
};

export const TRANSACTION_STATUS_TONE: Record<SalaryComponentTransactionStatus, StatusPillTone> = {
  DRAFT: 'neutral',
  LOCKED: 'info',
  COMPUTING: 'info',
  COMPUTED: 'info',
  APPROVED: 'success',
  POSTING: 'info',
  POSTED: 'success',
  FAILED: 'error',
  CANCELLED: 'neutral',
};

export const TRANSACTION_LINE_STATUS_LABEL: Record<TransactionLineStatus, string> = {
  DRAFT: 'Draft',
  PROCESSED: 'Processed',
  HELD: 'Held',
  RELEASE_PENDING: 'Release pending',
  RELEASED: 'Released',
};

export const TRANSACTION_LINE_STATUS_TONE: Record<TransactionLineStatus, StatusPillTone> = {
  DRAFT: 'neutral',
  PROCESSED: 'success',
  HELD: 'warning',
  RELEASE_PENDING: 'info',
  RELEASED: 'neutral',
};

/**
 * Line-level status pill content. Falls back to the parent transaction's
 * state when the line's own status doesn't map to a known label — e.g. an
 * approved transaction's lines should read "Approved" (green) even if the
 * backend hasn't stamped a per-line release status on them.
 */
export function transactionLineStatusView(
  status: TransactionLineStatus,
  transactionStatus: SalaryComponentTransactionStatus,
): { label: string; tone: StatusPillTone } {
  const knownLabel = TRANSACTION_LINE_STATUS_LABEL[status];
  if (knownLabel) {
    return { label: knownLabel, tone: TRANSACTION_LINE_STATUS_TONE[status] };
  }
  if (transactionStatus === 'APPROVED' || transactionStatus === 'POSTED') {
    return { label: 'Approved', tone: 'success' };
  }
  return { label: status ?? '—', tone: 'neutral' };
}
