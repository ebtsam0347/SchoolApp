// Row model and phase helpers behind the approval tracker (ApprovalTrack.tsx).

import type { ReactNode } from 'react';

import type { StatusPillTone } from '@/components';
import type { ApprovalStepDto } from '../types';

export type TrackedPerson = {
  fullName: string;
  employeeCode?: string | null;
  profilePictureUrl?: string | null;
};

/** One request, whatever its type — what the tracker and drawer render. */
export type TrackedRequestRow = {
  code: string;
  /** Who it's for. Null for a short-term batch, which covers several. */
  person: TrackedPerson | null;
  /** Short-term batch: who it covers, as text. */
  people?: string;
  /** Type-specific one-liner (dates, times, amount, …). */
  detail: ReactNode;
  /** Extra line under the detail in the drawer (e.g. the reason). */
  note?: ReactNode;
  statusLabel: string;
  statusTone: StatusPillTone;
  /** The type's own status value — drives `phaseOf`. */
  rawStatus: string;
  /** Stage it's waiting on; null once decided. */
  currentStage: {
    stageNo: number;
    stageRole: string;
    assignedToName: string | null;
  } | null;
  steps: ApprovalStepDto[];
  createdAt: string | null;
};

/* ------------------------------ phases ------------------------------ */

export type Phase = 'in-progress' | 'approved' | 'rejected' | 'cancelled';

export const PHASE_LABEL: Record<Phase, string> = {
  'in-progress': 'In progress',
  approved: 'Approved',
  rejected: 'Rejected',
  cancelled: 'Cancelled',
};

/** Advance statuses that are still moving through the chain. */
const IN_FLIGHT = new Set(['PENDING', 'MANAGER_APPROVED', 'HR_APPROVED']);

export type PhaseInput = Pick<TrackedRequestRow, 'rawStatus' | 'currentStage' | 'steps'>;

/**
 * Where a request is overall, across every type's own status vocabulary.
 * A rejected step wins over anything else; PARTIALLY_APPROVED leave and
 * APPROVED advances count as approved.
 */
export function phaseOf(row: PhaseInput): Phase {
  if (row.rawStatus === 'CANCELLED') return 'cancelled';
  if (row.rawStatus === 'REJECTED' || row.steps.some((st) => st.status === 'REJECTED')) {
    return 'rejected';
  }
  if (row.currentStage || IN_FLIGHT.has(row.rawStatus)) return 'in-progress';
  return 'approved';
}

/** "MANAGER" / "Manager Approval" / "DEPT_HEAD" → "Manager" / "Dept head". */
export function roleLabel(role: string): string {
  const r = role
    .replace(/\s*approval$/i, '')
    .replace(/_/g, ' ')
    .trim();
  if (r.length <= 3) return r.toUpperCase();
  return r.charAt(0).toUpperCase() + r.slice(1).toLowerCase();
}

/** The one-liner under the tracker: where it is, or how it ended. */
export function whereLine(row: PhaseInput): string {
  const phase = phaseOf(row);
  if (phase === 'in-progress') {
    const at = row.currentStage ?? row.steps.find((st) => st.status === 'PENDING');
    if (!at) return 'In progress';
    return `With ${roleLabel(at.stageRole)}${at.assignedToName ? ` · ${at.assignedToName}` : ''}`;
  }
  if (phase === 'rejected') {
    const at = row.steps.find((st) => st.status === 'REJECTED');
    return at ? `Rejected at ${roleLabel(at.stageRole)}` : 'Rejected';
  }
  if (phase === 'cancelled') return 'Cancelled';
  return row.steps.length ? 'Approved at every stage' : 'Approved';
}
