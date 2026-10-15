// /dashboards/hr — landed in D-5.

import { createFileRoute } from '@tanstack/react-router';

import { DashboardHR } from '@/dashboards';

export const Route = createFileRoute('/_app/dashboards/hr')({
  component: DashboardHR,
});
