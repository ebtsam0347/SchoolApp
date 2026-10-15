// /reports/jobs — async report submissions, polled until each settles.
// Same `*.reports.run` gate as /reports — anyone allowed to run a report
// is allowed to see their own submissions.

import { createFileRoute, redirect } from '@tanstack/react-router';

import { JobsPage } from '@/reports/jobs/JobsPage';
import { buildAbility } from '@/auth';
import { readSnapshot } from '@/lib';

interface JobsSearch {
  highlight?: number;
}

export const Route = createFileRoute('/_app/reports/jobs')({
  beforeLoad: () => {
    const snap = readSnapshot();
    if (!snap?.user) return;
    const ability = buildAbility(snap.user);
    const allowed =
      ability.can('run', 'employee.reports') ||
      ability.can('run', 'attendance.reports') ||
      ability.can('run', 'attendance.leaves.reports') ||
      ability.can('run', 'payroll.reports');
    if (!allowed) throw redirect({ to: '/' });
  },
  component: JobsPage,
  validateSearch: (search: Record<string, unknown>): JobsSearch => {
    const raw = search.highlight;
    const id =
      typeof raw === 'number'
        ? raw
        : typeof raw === 'string' && /^\d+$/.test(raw)
          ? Number(raw)
          : undefined;
    return id !== undefined ? { highlight: id } : {};
  },
});
