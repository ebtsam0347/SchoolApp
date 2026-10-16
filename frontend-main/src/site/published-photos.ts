// Published website photos, managed by admins under /admin/site-images.
//
// The Worker's public GET /media/slots returns the image assigned to each
// photo slot. It is fetched once per page load (and re-checked when the tab
// regains focus, so a replacement shows up without a manual refresh) and
// shared by every PhotoSlot. If the request fails, or nothing is published,
// PhotoSlot falls back to the bundled illustrated placeholder, so the public
// site never depends on the image store being reachable.

import { useSyncExternalStore } from 'react';

export type PublishedPhoto = { url: string; alt: string | null; updatedAt: string };
type Slots = Record<string, PublishedPhoto>;

const EMPTY: Slots = {};
let slots: Slots = EMPTY;
let inFlight: Promise<void> | null = null;
let lastFetch = 0;
const listeners = new Set<() => void>();

const REFRESH_AFTER_MS = 30_000;

function load(): Promise<void> {
  if (inFlight) return inFlight;
  lastFetch = Date.now();
  inFlight = fetch('/media/slots', { headers: { Accept: 'application/json' } })
    .then((res) => (res.ok ? (res.json() as Promise<{ slots?: Slots }>) : null))
    .then((body) => {
      const next = body?.slots && typeof body.slots === 'object' ? body.slots : null;
      if (next && JSON.stringify(next) !== JSON.stringify(slots)) {
        slots = next;
        listeners.forEach((l) => l());
      }
    })
    .catch(() => {
      /* keep placeholders / last known images */
    })
    .finally(() => {
      inFlight = null;
    });
  return inFlight;
}

function subscribe(listener: () => void) {
  listeners.add(listener);
  if (listeners.size === 1) {
    if (Date.now() - lastFetch > REFRESH_AFTER_MS) void load();
    document.addEventListener('visibilitychange', onVisible);
  }
  return () => {
    listeners.delete(listener);
    if (listeners.size === 0) document.removeEventListener('visibilitychange', onVisible);
  };
}

function onVisible() {
  if (document.visibilityState === 'visible' && Date.now() - lastFetch > REFRESH_AFTER_MS) void load();
}

/** The admin-published photo for a slot, or undefined to use the placeholder. */
export function usePublishedPhoto(slotKey: string): PublishedPhoto | undefined {
  const all = useSyncExternalStore(
    subscribe,
    () => slots,
    () => EMPTY,
  );
  return all[slotKey];
}
