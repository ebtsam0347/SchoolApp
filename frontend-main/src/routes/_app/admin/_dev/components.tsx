// /admin/_dev/components — live showcase for foundation components.
// Dev-only: in production builds the route bounces to / so the
// showcase code can be tree-shaken and the URL doesn't expose internal
// scaffolding. The showcase remains fully functional in `npm run dev`.

import { createFileRoute, redirect } from '@tanstack/react-router';

import { ComponentShowcase } from '@/pages/ComponentShowcase';

export const Route = createFileRoute('/_app/admin/_dev/components')({
  beforeLoad: () => {
    if (!import.meta.env.DEV) {
      throw redirect({ to: '/' });
    }
  },
  component: ComponentShowcase,
});
