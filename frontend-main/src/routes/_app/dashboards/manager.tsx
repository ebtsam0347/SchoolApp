// /dashboards/manager — landed in D-2.
// D-4 will resolve this from /dashboard based on the user's primary
// persona; for now it's directly addressable so we can audit the layout.

import { createFileRoute } from '@tanstack/react-router';

import { DashboardManager } from '@/dashboards';

export const Route = createFileRoute('/_app/dashboards/manager')({
  component: DashboardManager,
});
