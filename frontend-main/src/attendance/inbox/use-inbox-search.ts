// Server-side search for one bucket of the approvals inbox.
//
// `/approvals/inbox` accepts per-bucket filters (`leaveQ`, `shortLeaveQ`,
// `overtimeQ` + `overtimeType`, `regularizationQ`, `requisitionQ` +
// `requisitionType` + `requisitionDepartmentId`, …) and applies them to
// that bucket only. The filtered response is cached under its own key so
// the unfiltered inbox — shared with the sidebar badge, the KPI cards and
// the other tabs — is never replaced by a filtered copy. Mutations that
// invalidate `['bff', 'approvals', 'inbox']` refetch these too (prefix).

import { keepPreviousData, useQuery } from '@tanstack/react-query';
import { useDebounced } from '@/lib';
import {
  approvalsBff,
  type ApprovalsInboxParams,
  type ApprovalsInboxResponse,
} from '@/attendance';

const INBOX_KEY = ['bff', 'approvals', 'inbox'] as const;

export function useInboxSearch<Row>(
  /** Search text as typed; trimmed and debounced here. */
  search: string,
  /** Builds this bucket's params from the debounced text. */
  toParams: (q: string | undefined) => ApprovalsInboxParams,
  /** Extra filters that also make the search active (e.g. a type select). */
  extraActive: boolean,
  pick: (data: ApprovalsInboxResponse) => Row[] | undefined,
) {
  const q = useDebounced(search.trim(), 300);
  const active = q !== '' || extraActive;
  const params = toParams(q || undefined);
  const query = useQuery({
    queryKey: [...INBOX_KEY, 'search', params],
    queryFn: ({ signal }) => approvalsBff.inbox(signal, params),
    enabled: active,
    placeholderData: keepPreviousData,
    staleTime: 30_000,
  });
  return {
    /** True while any filter is set — the table should show `rows`. */
    active,
    rows: (query.data ? pick(query.data) : undefined) ?? [],
    fetching: active && query.isFetching,
    error: active && query.isError ? query.error : null,
  };
}
