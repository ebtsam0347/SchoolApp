// State-machine config for bonus runs, fed to the shared RunStateMachine.
//
// Bonus runs skip the payroll-run lock and post steps: a settings row is
// computed straight from DRAFT, and approval is the terminal state.

import type { RunStateMachineConfig } from '@/payroll/runs';
import { BONUS_RUN_STATUS_LABEL, BONUS_RUN_STATUS_TONE } from '@/payroll';

/** DRAFT → COMPUTED → APPROVED. */
export const BONUS_RUN_MACHINE: RunStateMachineConfig = {
  path: ['DRAFT', 'COMPUTED', 'APPROVED'],
  transitionLabels: {
    COMPUTED: 'Compute',
    APPROVED: 'Approve',
  },
  labels: BONUS_RUN_STATUS_LABEL,
  tones: BONUS_RUN_STATUS_TONE,
  descriptions: {
    DRAFT: 'Settings saved. Compute to calculate every employee’s bonus.',
    LOCKED: 'Locked.',
    COMPUTING: 'Calculating per-employee bonus amounts…',
    COMPUTED: 'Bonus transactions ready. Review and approve, or reset to change the settings.',
    APPROVED: 'Reviewed and signed off. Terminal state.',
    POSTING: 'Posting…',
    POSTED: 'Posted. Terminal state.',
    FAILED: 'Compute failed. Retry from the actions above.',
    CANCELLED: 'Cancelled.',
  },
  resetDescription:
    'Drops every computed bonus transaction and rolls back to Draft. Use to change the percentage or fix employee data before recomputing.',
  cancelledDescription:
    'This bonus run was cancelled. Create a new one for the same bonus to start again.',
};
