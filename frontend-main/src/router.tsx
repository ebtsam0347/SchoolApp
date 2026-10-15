import { createRouter } from '@tanstack/react-router';
import { routeTree } from './routeTree.gen';
import type { AuthState } from './auth';

export type RouterContext = {
  auth: AuthState;
};

export const router = createRouter({
  routeTree,
  context: { auth: { status: 'loading', user: null } satisfies AuthState },
  defaultPreload: 'intent',
  defaultPreloadStaleTime: 0,
  scrollRestoration: true,
});

declare module '@tanstack/react-router' {
  interface Register {
    router: typeof router;
  }
}
