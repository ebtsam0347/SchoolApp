// /dashboards/employee — landed in D-3.
// D-4 will resolve this from /dashboard based on the user's primary
// persona; for now it's directly addressable so we can audit the layout.

import { createFileRoute } from '@tanstack/react-router';

import { DashboardEmployee } from '@/dashboards';

export const Route = createFileRoute('/_app/dashboards/employee')({
  component: DashboardEmployee,
});
