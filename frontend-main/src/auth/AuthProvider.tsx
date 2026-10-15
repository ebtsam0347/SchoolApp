// AuthProvider — single source of truth for the user's session in the SPA.
//
// Responsibilities:
//   - Bootstrap from auth-storage on mount, then GET /auth/me to verify the
//     access token is still good. If it 401s the http client refreshes once;
//     if that fails too, we land in 'guest'.
//   - Expose login / logout / changePassword that talk to the gateway and
//     update both storage + in-memory state in one shot.
//   - Schedule a silent refresh ~60s before accessTokenExpiresAt so we never
//     hit a hard 401 mid-interaction.
//   - Re-sync if another tab logs in/out (subscribeAuthStorage hooks
//     localStorage events).

import { useEffect, useState, type ReactNode } from 'react';
import { http } from '@/lib/http';
import { isAuthFailure } from '@/lib/api-error';
// Direct file import (not '@/notifications'): that barrel pulls in hooks
// that import '@/auth' back.
import { releasePushOnLogout } from '@/notifications/push/web-push';
import {
  clearSnapshot,
  queryClient,
  readSnapshot,
  writeSnapshot,
  subscribeAuthStorage,
  readJwtExpMs,
} from '@/lib';
import type {
  AuthState,
  LoginRequest,
  PasswordChangeRequest,
  TokenResponse,
  UserIdentity,
} from './types';
import { AuthContext, type AuthContextValue } from './AuthContext';

const REFRESH_LEAD_MS = 60_000; // refresh 60s before expiry

function deriveStatus(user: UserIdentity): 'must-change-password' | 'authed' {
  return user.mustChangePassword ? 'must-change-password' : 'authed';
}

export function AuthProvider({ children }: { children: ReactNode }) {
  const [state, setState] = useState<AuthState>(() => {
    const snap = readSnapshot();
    if (!snap) return { status: 'guest', user: null };
    if (snap.user) {
      return { status: deriveStatus(snap.user), user: snap.user };
    }
    return { status: 'loading', user: null };
  });

  const refreshMe = async () => {
    try {
      const me = await http.get<UserIdentity>('/auth/me');
      const snap = readSnapshot();
      if (snap) writeSnapshot({ tokens: snap.tokens, user: me });
      setState({ status: deriveStatus(me), user: me });
    } catch (err) {
      // Only drop the session if the server explicitly rejected our
      // credentials. Transient 5xx / network errors leave the snapshot in
      // place so the user keeps working through backend hiccups.
      if (isAuthFailure(err)) {
        clearSnapshot();
        setState({ status: 'guest', user: null });
      }
    }
  };

  // Bootstrap: if we have tokens, verify them against /auth/me. Even if a
  // cached user is present we still revalidate — roles may have changed
  // since the snapshot was taken. Mount-only — refreshMe is referenced via
  // closure to avoid the dep churn that would otherwise loop.
  useEffect(() => {
    const snap = readSnapshot();
    if (!snap) {
      setState({ status: 'guest', user: null });
      return;
    }
    void (async () => {
      try {
        const me = await http.get<UserIdentity>('/auth/me');
        const cur = readSnapshot();
        if (cur) writeSnapshot({ tokens: cur.tokens, user: me });
        setState({ status: deriveStatus(me), user: me });
      } catch (err) {
        // Only force a logout if the server actually rejected the token.
        // On transient failures fall back to the cached user (if any) so
        // the user stays signed in across backend hiccups.
        if (isAuthFailure(err)) {
          clearSnapshot();
          setState({ status: 'guest', user: null });
          return;
        }
        const cur = readSnapshot();
        if (cur?.user) {
          setState({ status: deriveStatus(cur.user), user: cur.user });
        } else {
          setState({ status: 'guest', user: null });
        }
      }
    })();
  }, []);

  // Cross-tab sync.
  useEffect(() => {
    return subscribeAuthStorage(() => {
      const snap = readSnapshot();
      if (!snap) {
        setState({ status: 'guest', user: null });
        return;
      }
      if (snap.user) {
        setState({ status: deriveStatus(snap.user), user: snap.user });
      }
    });
  }, []);

  // Silent refresh scheduling. Self-contained: subscribes to snapshot changes
  // directly so a fresh expiry reschedules the timer, without leaning on
  // React state identity. (A `[state]` dep here used to interact badly with
  // the same-tab `emit()` from `writeSnapshot`: every refresh changed state
  // identity, which tore down + reinstalled the timer on every cycle.)
  useEffect(() => {
    let timer: number | undefined;
    const schedule = () => {
      if (timer !== undefined) window.clearTimeout(timer);
      const snap = readSnapshot();
      if (!snap) return;
      const expiresAt = readJwtExpMs(snap.tokens.accessToken);
      if (expiresAt === null) return;
      const fireIn = Math.max(5_000, expiresAt - Date.now() - REFRESH_LEAD_MS);
      timer = window.setTimeout(() => {
        void (async () => {
          try {
            const next = await http.post<TokenResponse>(
              '/auth/refresh',
              { refreshToken: snap.tokens.refreshToken },
              { skipAuth: true },
            );
            const cur = readSnapshot();
            writeSnapshot({ tokens: next, user: cur?.user ?? null });
          } catch (err) {
            // Refresh-token rejection is final — drop the session. Anything
            // else (gateway 5xx, network blip, timeout) is transient: leave
            // the snapshot alone and try again in 30s. Without this, every
            // upstream hiccup logged the user out mid-session.
            if (isAuthFailure(err)) {
              clearSnapshot();
              setState({ status: 'guest', user: null });
            } else {
              timer = window.setTimeout(schedule, 30_000);
            }
          }
        })();
      }, fireIn);
    };
    schedule();
    const unsub = subscribeAuthStorage(schedule);
    return () => {
      if (timer !== undefined) window.clearTimeout(timer);
      unsub();
    };
  }, []);

  const login = async (req: LoginRequest) => {
    const tokens = await http.post<TokenResponse>('/auth/login', req, {
      skipAuth: true,
    });
    writeSnapshot({ tokens, user: null });
    setState({ status: 'loading', user: null });
    const me = await http.get<UserIdentity>('/auth/me');
    writeSnapshot({ tokens, user: me });
    setState({ status: deriveStatus(me), user: me });
    // Query keys like the sidebar's pending-approvals count aren't scoped
    // per user, so anything cached from a previous session (or from the
    // brief pre-auth window) would otherwise be served as "fresh" for up
    // to `staleTime` — stale counts/data bleeding into the new session
    // until a hard reload wipes the in-memory QueryClient. Clearing here,
    // before any `enabled: !!auth.user` query re-fires for the new user,
    // guarantees a clean slate.
    queryClient.clear();
    return tokens;
  };

  const logout = async () => {
    // While the bearer is still valid: stop this browser's OS notifications
    // for the user signing out (the next user re-binds it on sign-in).
    await releasePushOnLogout();
    const snap = readSnapshot();
    if (snap?.tokens.refreshToken) {
      try {
        await http.post('/auth/logout', {
          refreshToken: snap.tokens.refreshToken,
        });
      } catch {
        // Server-side revoke is best-effort; we always clear locally.
      }
    }
    clearSnapshot();
    setState({ status: 'guest', user: null });
    queryClient.clear();
  };

  const changePassword = async (req: PasswordChangeRequest) => {
    // Endpoint rotates the session and returns a fresh token pair with
    // mustChangePassword cleared. Cache the new tokens BEFORE refreshMe so
    // the /auth/me call goes out with the new bearer (the previous JWT is
    // revoked when the session rotates).
    const tokens = await http.post<TokenResponse>(
      '/auth/password/change',
      req,
    );
    const cur = readSnapshot();
    writeSnapshot({ tokens, user: cur?.user ?? null });
    await refreshMe();
  };

  const value: AuthContextValue = {
    ...state,
    login,
    logout,
    changePassword,
    refreshMe,
  };

  return <AuthContext value={value}>{children}</AuthContext>;
}
