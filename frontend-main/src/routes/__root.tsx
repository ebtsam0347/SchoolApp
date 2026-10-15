import { createRootRouteWithContext, Outlet } from '@tanstack/react-router';
import { QueryClientProvider } from '@tanstack/react-query';
import { Suspense, lazy } from 'react';
import { ThemeProvider } from '@/theme';
import { AuthProvider, AbilityProvider } from '@/auth';
import { queryClient } from '@/lib/query';
import type { RouterContext } from '@/router';

// Devtools — only loaded outside production. The lazy() boundary keeps them
// out of the prod bundle entirely (router-plugin treeshakes the dynamic
// import). They render nothing in prod.
const RouterDevtools = import.meta.env.PROD
  ? () => null
  : lazy(() =>
      import('@tanstack/react-router-devtools').then((m) => ({
        default: m.TanStackRouterDevtools,
      })),
    );

const QueryDevtools = import.meta.env.PROD
  ? () => null
  : lazy(() =>
      import('@tanstack/react-query-devtools').then((m) => ({
        default: m.ReactQueryDevtools,
      })),
    );

export const Route = createRootRouteWithContext<RouterContext>()({
  component: RootComponent,
});

function RootComponent() {
  return (
    <QueryClientProvider client={queryClient}>
      <ThemeProvider>
        <AuthProvider>
          <AbilityProvider>
            <Outlet />
            <Suspense fallback={null}>
              <RouterDevtools position="bottom-right" />
              <QueryDevtools buttonPosition="bottom-left" />
            </Suspense>
          </AbilityProvider>
        </AuthProvider>
      </ThemeProvider>
    </QueryClientProvider>
  );
}
