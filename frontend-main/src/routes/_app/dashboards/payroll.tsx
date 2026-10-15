// /dashboards/payroll — landed in D-6.

import { createFileRoute } from '@tanstack/react-router';

import { DashboardPayroll } from '@/dashboards';

export const Route = createFileRoute('/_app/dashboards/payroll')({
  component: DashboardPayroll,
});
