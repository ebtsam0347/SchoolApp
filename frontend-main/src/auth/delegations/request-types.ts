// Display copy for the delegation `requestTypes` enum.
//
// Mirrors every queue in the Approvals Inbox (see routes/_app/inbox.index.tsx)
// so a delegation can cover the same ground the inbox does. LEAVE/SHORT_LEAVE/
// OVERTIME/REGULARIZATION are real backend `RequestType` values; the rest
// (REQUISITION, LONG_TERM_ADVANCE, SHORT_TERM_ADVANCE, TOUR_REQUEST) are
// UI-only placeholders until the backend's commons-approval delegation engine
// grows registrations for those queues too — see DelegationCreateDrawer for
// how the payload is filtered before it goes to the API.

export type DelegationRequestType =
  | 'LEAVE'
  | 'SHORT_LEAVE'
  | 'OVERTIME'
  | 'REGULARIZATION'
  | 'REQUISITION'
  | 'LONG_TERM_ADVANCE'
  | 'SHORT_TERM_ADVANCE'
  | 'TOUR_REQUEST';

export const REQUEST_TYPE_LABEL: Record<DelegationRequestType, string> = {
  LEAVE: 'Leave',
  SHORT_LEAVE: 'Short leave',
  OVERTIME: 'Overtime',
  REGULARIZATION: 'Regularization',
  REQUISITION: 'Requisitions',
  LONG_TERM_ADVANCE: 'Long-term advances',
  SHORT_TERM_ADVANCE: 'Short-term advances',
  TOUR_REQUEST: 'Travel requests',
};

export const ALL_REQUEST_TYPES: DelegationRequestType[] = [
  'LEAVE',
  'SHORT_LEAVE',
  'OVERTIME',
  'REGULARIZATION',
  'REQUISITION',
  'LONG_TERM_ADVANCE',
  'SHORT_TERM_ADVANCE',
  'TOUR_REQUEST',
];

/** Backend `RequestType` values the delegation API actually accepts today. */
export const BACKEND_SUPPORTED_REQUEST_TYPES: readonly DelegationRequestType[] = [
  'LEAVE',
  'SHORT_LEAVE',
  'OVERTIME',
  'REGULARIZATION',
];
