// `/` — the landing dashboard. Picks a persona layout via
// `useDashboardPersona()`; users with multiple personas can flip via
// the role switcher in the page header. See `DASHBOARD_PLAN.md` §D-4.

import { createFileRoute } from '@tanstack/react-router';

import { DashboardRouter } from '@/dashboards';

export const Route = createFileRoute('/_app/')({
  component: DashboardRouter,
});
