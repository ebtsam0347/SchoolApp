// Push opt-in state + actions, shared by the bell banner and
// /account/notifications (both read the same React Query cache).

import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';

import { useAuth } from '@/auth';

import { pushApi } from './api';
import { currentSubscription, disablePush, enablePush, pushSupport } from './web-push';

export const PUSH_CONFIG_KEY = ['push', 'config'] as const;
export const PUSH_STATE_KEY = ['push', 'state'] as const;

export type PushStatus =
  | 'loading'
  /** Server has no VAPID keys, or the browser has no Push API. */
  | 'unavailable'
  | 'insecure'
  | 'ios-needs-install'
  /** User blocked notifications for this site in browser settings. */
  | 'denied'
  | 'off'
  | 'on';

export function usePushConfig() {
  // Signed-in only: sign-out clears the query cache while the shell is still
  // mounted, and an unauthenticated refetch would just 401.
  const signedIn = useAuth().user != null;
  return useQuery({
    queryKey: PUSH_CONFIG_KEY,
    queryFn: ({ signal }) => pushApi.config(signal),
    enabled: signedIn && pushSupport() === 'supported',
    staleTime: Infinity,
    retry: false,
  });
}

export function usePushNotifications() {
  const qc = useQueryClient();
  const support = pushSupport();
  const config = usePushConfig();

  const state = useQuery({
    queryKey: PUSH_STATE_KEY,
    queryFn: async () => ({
      permission: Notification.permission,
      subscribed: (await currentSubscription()) !== null,
    }),
    enabled: support === 'supported',
    staleTime: Infinity,
  });

  const refresh = () => qc.invalidateQueries({ queryKey: PUSH_STATE_KEY });

  const enable = useMutation({
    mutationFn: () => enablePush(config.data?.publicKey ?? ''),
    onSettled: refresh,
  });
  const disable = useMutation({
    mutationFn: disablePush,
    onSettled: refresh,
  });

  let status: PushStatus;
  if (support === 'insecure' || support === 'ios-needs-install') status = support;
  else if (support === 'unsupported') status = 'unavailable';
  else if (config.isLoading || state.isLoading) status = 'loading';
  else if (!config.data?.enabled || !config.data.publicKey) status = 'unavailable';
  else if (state.data?.permission === 'denied') status = 'denied';
  else status = state.data?.permission === 'granted' && state.data.subscribed ? 'on' : 'off';

  return {
    status,
    /** Must be called from a click handler. Resolves to the browser permission. */
    enable: () => enable.mutateAsync(),
    disable: () => disable.mutateAsync(),
    pending: enable.isPending || disable.isPending,
  };
}
