// Deep links into /inbox — `?tab=overtime&open=67` switches to the Overtime
// tab and opens request 67's decision drawer. Notifications use this (see
// resolveNotificationLink in @/notifications).

import { useEffect, useRef } from 'react';

/** URL spelling of each /inbox tab. */
export const INBOX_TABS = [
  'leaves',
  'short-leave',
  'overtime',
  'regularization',
  'requisitions',
  'long-term-advances',
  'short-term-advances',
  'tour-requests',
] as const;

export type InboxTab = (typeof INBOX_TABS)[number];

export const isInboxTab = (v: unknown): v is InboxTab =>
  INBOX_TABS.includes(v as InboxTab);

/**
 * Open the row whose `request.id` is `openId` as soon as `rows` contain it,
 * then call `onOpened` (the page drops `open` from the URL there, so closing
 * the drawer doesn't reopen it and the same link works again later).
 */
export function useOpenRequestFromLink<R extends { request: { id: number } }>(
  rows: readonly R[],
  openId: number | undefined,
  open: (row: R) => void,
  onOpened?: () => void,
) {
  useOpenFromLink(rows, openId, requestIdOf, open, onOpened);
}

const requestIdOf = (r: { request: { id: number } }) => r.request.id;

/** Same, for rows whose id isn't at `request.id` (requisitions, advances, travel). */
export function useOpenFromLink<R>(
  rows: readonly R[],
  openId: number | undefined,
  idOf: (row: R) => number,
  open: (row: R) => void,
  onOpened?: () => void,
) {
  const handled = useRef<number | undefined>(undefined);
  useEffect(() => {
    if (openId == null) {
      handled.current = undefined;
      return;
    }
    if (handled.current === openId) return;
    const row = rows.find((r) => idOf(r) === openId);
    if (!row) return;
    handled.current = openId;
    open(row);
    onOpened?.();
  }, [rows, openId, idOf, open, onOpened]);
}
