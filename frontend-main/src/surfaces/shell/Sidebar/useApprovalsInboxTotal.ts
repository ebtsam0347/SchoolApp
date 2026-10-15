// Pending-count badge for the sidebar's "Approvals Inbox" row.
//
// Mirrors the same "To review" total /inbox itself shows — sum across every
// queue. All of it (leaves/short leave/overtime/regularization/requisitions/
// advances/travel) comes from the single `/approvals/inbox` composite now;
// the standalone `/short-leaves/pending-approvals` endpoint 404s (never
// shipped), so short leave is read from the composite's `shortLeave` slice
// like everything else. Same query key as the Inbox page, so react-query
// dedupes the fetch between the two — whichever mounts first pays the
// round-trip, the other just reads the cache.

import { useQuery } from '@tanstack/react-query';
import { useAuth } from '@/auth';
import { approvalsBff } from '@/attendance';

const STALE_TIME = 30_000;

/** Total pending count across every approvals queue, or `undefined` while any queue is still loading. */
export function useApprovalsInboxTotal(): number | undefined {
  const auth = useAuth();
  const enabled = !!auth.user;

  const inboxQuery = useQuery({
    queryKey: ['bff', 'approvals', 'inbox'],
    queryFn: ({ signal }) => approvalsBff.inbox(signal),
    enabled,
    staleTime: STALE_TIME,
  });

  if (inboxQuery.data === undefined) {
    return undefined;
  }

  const data = inboxQuery.data;
  // Composite count when present, row count as the fallback — same pair the
  // Inbox page's own badges use, so the two never disagree. No travel-type
  // filter: the queue surfaces LOCAL *and* INTERNATIONAL, and filtering to
  // LOCAL here made the badge undercount every international request.
  //
  // Both slices are read through `?? []` / `?? 0`: the backend omits a slice
  // whose fan-in failed, and this hook renders in the sidebar on every route,
  // so a bare `.map()` here white-screens the whole app rather than one page.
  const travelRequestCount = data.counts.travelRequests ?? data.travelRequests?.length ?? 0;
  const shortLeaveCount = data.counts.shortLeave ?? data.shortLeave?.length ?? 0;

  return (
    data.counts.leaves +
    shortLeaveCount +
    data.counts.overtime +
    data.counts.regularization +
    data.counts.requisitions +
    data.counts.permanentAdvances +
    data.counts.shortTermAdvances +
    travelRequestCount
  );
}
