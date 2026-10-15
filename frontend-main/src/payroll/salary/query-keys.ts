// Shared query key + hook for the salary tab bundle. Every section calls
// `useSalaryTab(employeeId)` and React Query dedupes to a single
// `GET /payroll/people/{id}/salary` per tab open. Mutations invalidate this
// key to refresh every section in one round-trip.

import { useQuery } from '@tanstack/react-query';
import { payrollComposites } from '@/payroll';

export function salaryTabKey(employeeId: number) {
  return ['payroll', 'salary', 'tab', employeeId] as const;
}

export function useSalaryTab(employeeId: number) {
  return useQuery({
    queryKey: salaryTabKey(employeeId),
    queryFn: ({ signal }) => payrollComposites.salaryTab(employeeId, signal),
    staleTime: 30_000,
  });
}
