// State-machine configs for the shared RunStateMachine visualization.
//
// A config pins the happy path (ordered canonical states, the last one
// terminal), the edge labels between them, and the copy shown for each
// state. Payroll runs use the five-step machine below; bonus runs pass a
// shorter one (see src/payroll/bonuses/machine.ts).

import type { StatusPillTone } from '@/components';
import { PAYROLL_RUN_STATUS_LABEL, PAYROLL_RUN_STATUS_TONE } from '../format';
import type { PayrollRunStatus } from '../types';

export type RunStateMachineConfig = {
  /** Canonical states a healthy run flows through, in order. The last one is terminal. */
  path: readonly PayrollRunStatus[];
  /** Label for the edge leading *into* each path state. */
  transitionLabels: Partial<Record<PayrollRunStatus, string>>;
  labels: Record<PayrollRunStatus, string>;
  tones: Record<PayrollRunStatus, StatusPillTone>;
  /** One-liner under the current-state pill. */
  descriptions: Record<PayrollRunStatus, string>;
  /** Copy for the Reset side-route shown in COMPUTED. */
  resetDescription: string;
  /** Copy for the Cancelled side-route. */
  cancelledDescription: string;
};

/** DRAFT → LOCKED → COMPUTED → APPROVED → POSTED. */
export const PAYROLL_RUN_MACHINE: RunStateMachineConfig = {
  path: ['DRAFT', 'LOCKED', 'COMPUTED', 'APPROVED', 'POSTED'],
  transitionLabels: {
    LOCKED: 'Lock attendance',
    COMPUTED: 'Compute',
    APPROVED: 'Approve',
    POSTED: 'Post',
  },
  labels: PAYROLL_RUN_STATUS_LABEL,
  tones: PAYROLL_RUN_STATUS_TONE,
  descriptions: {
    DRAFT: 'Run created. Lock attendance to begin.',
    LOCKED: 'Attendance frozen for the period. Compute next.',
    COMPUTING: 'Per-employee math in progress…',
    COMPUTED: 'Payslips ready. Review and approve, or reset to fix data.',
    APPROVED: 'Reviewed and signed off. Post to disburse.',
    POSTING: 'Generating PDFs and firing per-employee events…',
    POSTED: 'PDFs generated, events fired. Terminal state.',
    FAILED: 'A transition failed. Inspect the run and retry.',
    CANCELLED: 'Cancelled before posting.',
  },
  resetDescription:
    'Drops every payslip on this run and rolls back to Locked. Use to fix employee data before recomputing.',
  cancelledDescription:
    'The run was cancelled before posting. Open a new run for the same period to start again.',
};
