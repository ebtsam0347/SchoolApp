import { QueryClient } from '@tanstack/react-query';
import { isApiError } from './api-error';

// Single client for the app. Defaults tuned for HRMS data:
// - 30s staleTime keeps hot lists from refetching on every nav
// - retry once for transient 5xx; never retry 4xx (validation/auth errors
//   come back the same on a second call)
// - no refetch on focus — HRMS surfaces aren't dashboards-of-truth, and
//   focus-refetch fights with the silent-refresh cycle in AuthProvider
export const queryClient = new QueryClient({
  defaultOptions: {
    queries: {
      staleTime: 30_000,
      gcTime: 5 * 60_000,
      refetchOnWindowFocus: false,
      retry: (failureCount, error) => {
        if (isApiError(error) && error.status >= 400 && error.status < 500) {
          return false;
        }
        return failureCount < 1;
      },
    },
    mutations: {
      retry: false,
    },
  },
});
