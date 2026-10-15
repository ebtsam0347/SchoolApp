// User-id → display info cache. The authoritative resolver is the BFF
// endpoint `/bff/users/by-ids` (see `bff-api.ts`); this module is the
// in-memory store + a hook that lazily resolves missing ids in batches.
//
// Why a custom store and not just TanStack Query: every chat surface needs
// to look up names by `userId` synchronously while rendering bubbles, rows,
// member lists, and the typing indicator. A query-per-surface would refetch
// constantly; the custom store gives us a single shared cache that survives
// across queries and SSE invalidations.

import { useEffect, useSyncExternalStore } from 'react';
import { useQuery } from '@tanstack/react-query';
import { chatBff } from './bff-api';
import type { UserIdentitySummary } from './types';

export type UserDisplay = {
  fullName: string;
  username?: string | null;
  employeeCode?: string | null;
  profilePictureUrl?: string | null;
};

const cache = new Map<number, UserDisplay>();
const listeners = new Set<() => void>();

function notify() {
  for (const l of listeners) l();
}

function eq(a: UserDisplay | undefined, b: UserDisplay): boolean {
  if (!a) return false;
  return (
    a.fullName === b.fullName &&
    (a.username ?? null) === (b.username ?? null) &&
    (a.employeeCode ?? null) === (b.employeeCode ?? null) &&
    (a.profilePictureUrl ?? null) === (b.profilePictureUrl ?? null)
  );
}

export function seedUser(userId: number, display: UserDisplay) {
  if (eq(cache.get(userId), display)) return;
  cache.set(userId, display);
  notify();
}

export function seedUsers(entries: Array<{ userId: number; display: UserDisplay }>) {
  let changed = false;
  for (const { userId, display } of entries) {
    if (!eq(cache.get(userId), display)) {
      cache.set(userId, display);
      changed = true;
    }
  }
  if (changed) notify();
}

/**
 * Same as `seedUsers` but only fills entries that don't already exist.
 * Use for low-confidence sources (e.g. message.senderUsername) so they
 * can't overwrite a richer entry seeded from `/bff/users/by-ids`.
 */
export function seedUsersFallback(entries: Array<{ userId: number; display: UserDisplay }>) {
  let changed = false;
  for (const { userId, display } of entries) {
    if (cache.has(userId)) continue;
    cache.set(userId, display);
    changed = true;
  }
  if (changed) notify();
}

/** Convert a BFF `UserIdentitySummary` to a `UserDisplay`. */
export function fromIdentitySummary(u: UserIdentitySummary): UserDisplay {
  return {
    fullName: u.fullName,
    username: u.username,
    employeeCode: u.employeeCode,
    profilePictureUrl: u.profilePictureUrl,
  };
}

/** Seed from a `Record<userId, UserIdentitySummary>` (the inbox composite shape). */
export function seedFromResolved(resolved: Record<string, UserIdentitySummary>) {
  const entries: Array<{ userId: number; display: UserDisplay }> = [];
  for (const [k, v] of Object.entries(resolved)) {
    const userId = Number(k);
    if (!Number.isFinite(userId)) continue;
    entries.push({ userId, display: fromIdentitySummary(v) });
  }
  if (entries.length > 0) seedUsers(entries);
}

export function lookupUser(userId: number): UserDisplay | undefined {
  return cache.get(userId);
}

export function userDisplayName(userId: number): string {
  return cache.get(userId)?.fullName ?? `User #${userId}`;
}

function subscribe(cb: () => void) {
  listeners.add(cb);
  return () => listeners.delete(cb);
}

/** React hook — re-renders when the cache mutates. */
export function useUserDisplay(userId: number | undefined): UserDisplay | undefined {
  return useSyncExternalStore(
    subscribe,
    () => (userId == null ? undefined : cache.get(userId)),
    () => undefined,
  );
}

/** Convenience: name with `User #N` fallback. */
export function useUserName(userId: number | undefined): string {
  const d = useUserDisplay(userId);
  if (userId == null) return '';
  return d?.fullName ?? `User #${userId}`;
}

/**
 * Trigger a `/bff/users/by-ids` resolve for any of the given userIds that
 * aren't already in the cache. Use this on every chat surface that surfaces
 * userIds (member lists, message bubbles) so names resolve without depending
 * on directory permission. The resolver query is keyed on the missing-ids
 * set so consumers across the page share one round-trip.
 */
export function useResolveUsers(userIds: ReadonlyArray<number | null | undefined>) {
  // Compute missing ids once per render. We don't memoise because the
  // identity is derived from the input array — the caller is responsible
  // for not changing the array unnecessarily.
  const ids: number[] = [];
  const seen = new Set<number>();
  for (const u of userIds) {
    if (u == null) continue;
    if (seen.has(u)) continue;
    seen.add(u);
    if (!cache.has(u)) ids.push(u);
  }
  ids.sort((a, b) => a - b);
  const key = ids.join(',');

  const query = useQuery({
    queryKey: ['chat', 'users-by-ids', key],
    queryFn: ({ signal }) => chatBff.usersByIds(ids, signal),
    enabled: ids.length > 0,
    staleTime: 5 * 60_000,
    gcTime: 10 * 60_000,
  });

  useEffect(() => {
    if (!query.data) return;
    seedUsers(
      query.data.users.map((u) => ({
        userId: u.userId,
        display: fromIdentitySummary(u),
      })),
    );
  }, [query.data]);
}
