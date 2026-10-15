// /dashboards/admin — landed in D-7.

import { createFileRoute } from '@tanstack/react-router';

import { DashboardAdmin } from '@/dashboards';

export const Route = createFileRoute('/_app/dashboards/admin')({
  component: DashboardAdmin,
});
