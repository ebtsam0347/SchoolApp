// /inbox — combined approvals inbox (managers + HR).
//
// Leaves / Short leave / Overtime / Regularization / Requisitions /
// Long-term advances / Short-term advances / Travel(factory tour) are all
// fed by the matching slice of the single `/approvals/inbox` composite
// (`approvalsBff.inbox`) — one round-trip fans out across attendance-
// service, requisition-service, payroll-service, and the factory-tour
// store server-side. (Short leave used to have its own dedicated
// `/short-leaves/pending-approvals` query, but that endpoint 404s — never
// shipped — so it's sourced from the composite's `shortLeave` slice too.)
// Rows are stage-aware; the action buttons render only what `actions[]`
// says the caller can do, so the same page handles MANAGER, HR, and
// delegates uniformly.
//
// The per-segment tables + drawers live in src/attendance/inbox/. This
// route file owns just the page scaffold + tab switching.
//
// The active tab lives in the URL (`?tab=overtime`), and `?open=<id>` opens
// that request's decision drawer once the inbox loads — notifications deep
// link here that way (see resolveNotificationLink in @/notifications).

import { useCallback, useState } from 'react';
import { createFileRoute, Link, useNavigate } from '@tanstack/react-router';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import {
  Alert,
  App as AntdApp,
  Button,
  Card,
  Segmented,
  Select,
  Space,
  Tooltip,
  theme as antdTheme,
} from 'antd';
import dayjs from 'dayjs';
import {
  Banknote,
  CheckCircle2,
  CheckSquare,
  ClipboardCheck,
  History,
  Inbox as InboxIcon,
  MapPin,
  Plane,
  RefreshCcw,
  Sparkles,
  Stamp,
  Timer,
  Wallet,
  XCircle,
} from 'lucide-react';

import {
  EmptyState,
  FilterBar,
  KpiGrid,
  PageHeader,
  RejectReasonModal,
  SegmentLabel,
  StatCard,
} from '@/components';
import { useAuth } from '@/auth';
import {
  approvalsBff,
  permanentAdvanceEmployees,
  unwrapRequest,
  type ApprovalRequestType,
} from '@/attendance';
import {
  isInboxTab,
  LeavesInbox,
  OvertimeInbox,
  RegularizationInbox,
  ShortLeaveInbox,
  useInboxSearch,
  useOpenFromLink,
  type InboxTab,
  type ShortLeaveInboxRow,
} from '@/attendance/inbox';
import { catalogsApi } from '@/people';
import {
  REQUISITION_TYPE_LABEL,
  RequisitionDetailDrawer,
  RequisitionTable,
  requisitionApi,
  type RequisitionDto,
  type RequisitionStatus,
  type RequisitionType,
} from '@/requisition';
import {
  permanentAdvancesApi,
  resolveAdvanceApprovalStage,
  shortTermAdvancesApi,
} from '@/payroll';
import {
  PermanentAdvanceDetailDrawer,
  PermanentAdvanceTable,
  ShortTermAdvanceBatchDetailDrawer,
  ShortTermAdvanceBatchTable,
} from '@/payroll/advances';
import type { AdvanceStatus, PermanentAdvanceDto, ShortTermAdvanceBatchDto } from '@/payroll';
import { FactoryTourDetailDrawer, FactoryTourTable, type FactoryTourDto } from '@/factory-tour';
import { toFactoryTourDto } from '@/travel';
import { getErrorMessage } from '@/lib';

const RAW_ADVANCE_STATUS: Record<AdvanceStatus, string> = {
  PENDING: 'PENDING',
  MANAGER_APPROVED: 'MANAGER_APPROVED',
  HR_APPROVED: 'HR_APPROVED',
  APPROVED: 'APPROVED',
  REJECTED: 'REJECTED',
  CANCELLED: 'CANCELLED',
};

// Requisition Status shows the stored status verbatim, like advances above.
const RAW_REQUISITION_STATUS: Record<RequisitionStatus, string> = {
  DRAFT: 'DRAFT',
  PENDING_APPROVAL: 'PENDING_APPROVAL',
  APPROVED: 'APPROVED',
  REJECTED: 'REJECTED',
  FULFILLED: 'FULFILLED',
  CANCELLED: 'CANCELLED',
};

type InboxKind =
  | 'LEAVES'
  | 'SHORT_LEAVE'
  | 'OVERTIME'
  | 'REGULARIZATION'
  | 'REQUISITIONS'
  | 'LONG_TERM_ADVANCES'
  | 'SHORT_TERM_ADVANCES'
  | 'TOUR_REQUESTS';

// No-op — pending-approval requisition rows only ever render Approve/Reject
// (see RequisitionRowActions), never the Edit button, so this is never
// actually invoked from this queue.
function noopEdit() {}

/**
 * Count of decisions the caller made for one request type in a date window
 * — `size: 1` read for just `totalElements`, since /approvals/history has
 * no dedicated count endpoint.
 */
function useDecisionCounts(from: string, to: string, enabled: boolean) {
  // One composite read for the whole window, counted client-side.
  //
  // This used to be six `size: 1` reads of `totalElements`. The gateway
  // rewrites /approvals/history to the BFF composite, which answers
  // `{ rows, total }` and ignores type/decision/size — so every one of
  // those counts silently resolved to undefined. Same root cause as the
  // empty table on /inbox/history; see approvalsBff.history.
  const query = useQuery({
    queryKey: ['approvals', 'history', from, to],
    queryFn: ({ signal }) => approvalsBff.history({ from, to }, signal),
    enabled,
    staleTime: 30_000,
  });

  const countOf = (
    type: ApprovalRequestType | readonly ApprovalRequestType[],
    decision: 'APPROVED' | 'REJECTED',
  ) =>
    (query.data?.rows ?? []).reduce(
      (n, r) =>
        (typeof type === 'string' ? r.type === type : type.includes(r.type)) &&
        r.decision === decision
          ? n + 1
          : n,
      0,
    );

  return { query, countOf };
}

/** Small "N approved · N rejected" line for a StatCard's delta slot. */
function DecisionBreakdown({ approved, rejected }: { approved: number; rejected: number }) {
  const { token } = antdTheme.useToken();
  // Each half stays on one line; in a narrow card the pair wraps as two
  // clean left-aligned lines instead of splitting mid-phrase.
  return (
    <span style={{ display: 'flex', flexWrap: 'wrap', columnGap: 8, rowGap: 2 }}>
      <span style={{ color: token.colorSuccess, fontWeight: 600, whiteSpace: 'nowrap' }}>
        {approved} approved
      </span>
      <span style={{ color: token.colorError, fontWeight: 600, whiteSpace: 'nowrap' }}>
        {rejected} rejected
      </span>
    </span>
  );
}

const TAB_TO_KIND: Record<InboxTab, InboxKind> = {
  leaves: 'LEAVES',
  'short-leave': 'SHORT_LEAVE',
  overtime: 'OVERTIME',
  regularization: 'REGULARIZATION',
  requisitions: 'REQUISITIONS',
  'long-term-advances': 'LONG_TERM_ADVANCES',
  'short-term-advances': 'SHORT_TERM_ADVANCES',
  'tour-requests': 'TOUR_REQUESTS',
};
const idOf = (row: { id: number }) => row.id;

const KIND_TO_TAB = Object.fromEntries(
  Object.entries(TAB_TO_KIND).map(([tab, kind]) => [kind, tab]),
) as Record<InboxKind, InboxTab>;

// Tighter than StatCard's default 18px so all nine cards fit on one row.
const SUMMARY_CARD_STYLES = { body: { padding: '12px 14px' } };

type InboxSearch = {
  tab?: InboxTab;
  /** Request id whose decision drawer to open (attendance tabs). */
  open?: number;
};

export const Route = createFileRoute('/_app/inbox/')({
  validateSearch: (search): InboxSearch => {
    const open = Number(search.open);
    return {
      tab: isInboxTab(search.tab) ? search.tab : undefined,
      open: Number.isInteger(open) && open > 0 ? open : undefined,
    };
  },
  component: InboxPage,
});

function InboxPage() {
  const auth = useAuth();
  const { message } = AntdApp.useApp();
  const queryClient = useQueryClient();
  const urlSearch = Route.useSearch();
  const navigate = useNavigate({ from: '/inbox/' });
  const kind: InboxKind = urlSearch.tab ? TAB_TO_KIND[urlSearch.tab] : 'LEAVES';
  const openId = urlSearch.open;
  // Drawer opened from a deep link — drop `open` so closing it doesn't
  // reopen it, and the same notification works again later.
  const clearOpen = useCallback(
    () => void navigate({ search: (prev) => ({ tab: prev.tab }), replace: true }),
    [navigate],
  );
  // Search box for the tabs whose table is a shared component (reused on
  // other pages, so it can't grow its own search/sorter without changing
  // behavior there too) — filtered here, at the wrapper level, instead.
  // Cleared on tab switch so a stale query doesn't hide the next tab's rows.
  const [search, setSearch] = useState('');
  // Bulk-select checkboxes for the three tabs whose table is a shared
  // component (Requisitions / Long-term advances / Short-term advances —
  // same bulk-approve UX Overtime and the other attendance tabs already
  // have). Cleared on tab switch for the same reason `search` is.
  const [selectedKeys, setSelectedKeys] = useState<React.Key[]>([]);
  const [batchRejectOpen, setBatchRejectOpen] = useState(false);
  // Read-only "Details" drawers — mirror the per-type decision drawers the
  // attendance tabs use.
  const [viewingRequisition, setViewingRequisition] = useState<RequisitionDto | null>(null);
  const [viewingPermanentAdvance, setViewingPermanentAdvance] =
    useState<PermanentAdvanceDto | null>(null);
  const [viewingShortTermAdvance, setViewingShortTermAdvance] =
    useState<ShortTermAdvanceBatchDto | null>(null);
  const [viewingFactoryTour, setViewingFactoryTour] = useState<FactoryTourDto | null>(null);
  // Requisitions-tab filters, sent server-side with the search text.
  const [requisitionType, setRequisitionType] = useState<RequisitionType | undefined>(undefined);
  const [requisitionDepartmentId, setRequisitionDepartmentId] = useState<number | undefined>(
    undefined,
  );
  const changeKind = (v: InboxKind) => {
    void navigate({ search: { tab: KIND_TO_TAB[v] }, replace: true });
    setSearch('');
    setRequisitionType(undefined);
    setRequisitionDepartmentId(undefined);
    setSelectedKeys([]);
  };

  // Single composite (BFF T3) — fans out 3 pending lists + employee
  // enrichment server-side and returns counts.
  const inboxQuery = useQuery({
    queryKey: ['bff', 'approvals', 'inbox'],
    queryFn: ({ signal }) => approvalsBff.inbox(signal),
    enabled: !!auth.user,
    staleTime: 30_000,
  });

  // Approved/rejected breakdown per category, so each KPI card shows more
  // than just what's pending. Leave/Overtime/Regularization decisions come
  // from the decision-history endpoint (§/inbox/history) — there's no count
  // endpoint, so each combination is a `size: 1` request read for just its
  // `totalElements`, same trick the admin Requisitions page already uses.
  // Windowed to the last 30 days to match /inbox/history's own default.
  const decidedTo = dayjs().format('YYYY-MM-DD');
  const decidedFrom = dayjs().subtract(30, 'day').format('YYYY-MM-DD');
  const hasUser = !!auth.user;
  const decisions = useDecisionCounts(decidedFrom, decidedTo, hasUser);
  const leavesApproved = decisions.countOf('LEAVE', 'APPROVED');
  const leavesRejected = decisions.countOf('LEAVE', 'REJECTED');
  const overtimeApproved = decisions.countOf('OVERTIME', 'APPROVED');
  const overtimeRejected = decisions.countOf('OVERTIME', 'REJECTED');
  const regularizationApproved = decisions.countOf('REGULARIZATION', 'APPROVED');
  const regularizationRejected = decisions.countOf('REGULARIZATION', 'REJECTED');

  // Requisitions come through the same decision-history read (both of
  // their chain types), so this card matches the others: the caller's own
  // decisions in the window — no separate /requisitions count calls.
  const REQUISITION_TYPES = ['REQUISITION_NEW_HIRE', 'REQUISITION_REPLACEMENT'] as const;
  const requisitionsApproved = decisions.countOf(REQUISITION_TYPES, 'APPROVED');
  const requisitionsRejected = decisions.countOf(REQUISITION_TYPES, 'REJECTED');

  // Requisitions / advances / travel requests are now fanned into the same
  // `/approvals/inbox` composite as leaves/overtime/regularization — no more
  // separate per-status pending-list calls for them (see `data.requisitions`
  // / `data.permanentAdvances` / `data.shortTermAdvances` /
  // `data.travelRequests` below).

  // Short leave: the standalone `/short-leaves/pending-approvals` endpoint
  // 404s (never shipped) — sourced from the same composite as leaves/
  // overtime/regularization instead (see `data.shortLeave` below).
  const departmentsQuery = useQuery({
    queryKey: ['catalog', 'departments'],
    queryFn: ({ signal }) => catalogsApi.departments(signal),
    staleTime: 5 * 60_000,
    enabled: !!auth.user,
  });
  const sectionsQuery = useQuery({
    queryKey: ['catalog', 'sections'],
    queryFn: ({ signal }) => catalogsApi.sections(undefined, signal),
    staleTime: 5 * 60_000,
    enabled: !!auth.user,
  });
  const designationsQuery = useQuery({
    queryKey: ['catalog', 'designations'],
    queryFn: ({ signal }) => catalogsApi.designations(signal),
    staleTime: 5 * 60_000,
    enabled: !!auth.user,
  });
  const gradesQuery = useQuery({
    queryKey: ['catalog', 'grades'],
    queryFn: ({ signal }) => catalogsApi.grades(signal),
    staleTime: 5 * 60_000,
    enabled: !!auth.user,
  });

  const data = inboxQuery.data;
  const requisitionRows = (data?.requisitions ?? []).map(unwrapRequest);
  const longTermAdvanceRows = (data?.permanentAdvances ?? []).map(unwrapRequest);
  const shortTermAdvanceRows = (data?.shortTermAdvances ?? []).map(unwrapRequest);
  // Both LOCAL and INTERNATIONAL requests belong in the approvals queue —
  // there used to be a LOCAL-only filter here, which silently hid every
  // international travel request from approvers.
  //
  // The composite carries this slice as the raw /travel wire shape
  // (requestNo / requesterEmployeeName / legs[] / participants[]) — not the
  // flat DTO the table and drawer render. Handing it over unadapted left
  // every column blank; this is the same adapter the self-service Travel
  // Request page already runs its list through.
  const tourRequestRows = (data?.travelRequests ?? [])
    .map(unwrapRequest)
    .map(toFactoryTourDto);

  // Deep link (`?open=<id>`) for the tabs whose rows open a Details drawer
  // here — the attendance tabs handle it inside their own tables.
  useOpenFromLink(
    requisitionRows,
    kind === 'REQUISITIONS' ? openId : undefined,
    idOf,
    setViewingRequisition,
    clearOpen,
  );
  useOpenFromLink(
    longTermAdvanceRows,
    kind === 'LONG_TERM_ADVANCES' ? openId : undefined,
    idOf,
    setViewingPermanentAdvance,
    clearOpen,
  );
  useOpenFromLink(
    shortTermAdvanceRows,
    kind === 'SHORT_TERM_ADVANCES' ? openId : undefined,
    idOf,
    setViewingShortTermAdvance,
    clearOpen,
  );
  useOpenFromLink(
    tourRequestRows,
    kind === 'TOUR_REQUESTS' ? openId : undefined,
    idOf,
    setViewingFactoryTour,
    clearOpen,
  );

  // A deep link to a request that is no longer in this queue (decided, or
  // past the caller's stage) — say so instead of silently showing the list.
  const idsInTab: number[] =
    kind === 'LEAVES'
      ? (data?.leaves ?? []).map((r) => r.request.id)
      : kind === 'SHORT_LEAVE'
        ? (data?.shortLeave ?? []).map((r) => r.request.id)
        : kind === 'OVERTIME'
          ? (data?.overtime ?? []).map((r) => r.request.id)
          : kind === 'REGULARIZATION'
            ? (data?.regularization ?? []).map((r) => r.request.id)
            : kind === 'REQUISITIONS'
              ? requisitionRows.map(idOf)
              : kind === 'LONG_TERM_ADVANCES'
                ? longTermAdvanceRows.map(idOf)
                : kind === 'SHORT_TERM_ADVANCES'
                  ? shortTermAdvanceRows.map(idOf)
                  : tourRequestRows.map(idOf);
  const missingLink = openId != null && !!data && !idsInTab.includes(openId);

  const searchQuery = search.trim().toLowerCase();

  // Server-side search (`requisitionQ` + `requisitionType` +
  // `requisitionDepartmentId`) — see useInboxSearch. The shared `search`
  // text only counts on this tab, so typing on another tab doesn't fire it.
  const requisitionSearch = useInboxSearch(
    kind === 'REQUISITIONS' ? search : '',
    (requisitionQ) => ({ requisitionQ, requisitionType, requisitionDepartmentId }),
    kind === 'REQUISITIONS' && (requisitionType != null || requisitionDepartmentId != null),
    (d) => d.requisitions?.map(unwrapRequest),
  );
  const filteredRequisitionRows = requisitionSearch.active
    ? requisitionSearch.rows
    : requisitionRows;

  // Server-side search (`permanentAdvanceQ`: advance id exact, or substring
  // of request code / employee code / employee name). Rows carry their
  // employee summary inline, so the table needs no by-ids lookup.
  const longTermAdvanceSearch = useInboxSearch(
    kind === 'LONG_TERM_ADVANCES' ? search : '',
    (permanentAdvanceQ) => ({ permanentAdvanceQ }),
    false,
    (d) => d.permanentAdvances,
  );
  const longTermAdvanceSource = longTermAdvanceSearch.active
    ? longTermAdvanceSearch.rows
    : (data?.permanentAdvances ?? []);
  const filteredLongTermAdvanceRows = longTermAdvanceSource.map(unwrapRequest);
  const longTermAdvanceEmployees = permanentAdvanceEmployees(longTermAdvanceSource);

  // Server-side search (`shortTermAdvanceQ`: batch id exact, or substring
  // of request code) — same pattern as Requisitions above.
  const shortTermAdvanceSearch = useInboxSearch(
    kind === 'SHORT_TERM_ADVANCES' ? search : '',
    (shortTermAdvanceQ) => ({ shortTermAdvanceQ }),
    false,
    (d) => d.shortTermAdvances?.map(unwrapRequest),
  );
  const filteredShortTermAdvanceRows = shortTermAdvanceSearch.active
    ? shortTermAdvanceSearch.rows
    : shortTermAdvanceRows;

  const filteredTourRequestRows = !searchQuery
    ? tourRequestRows
    : tourRequestRows.filter((r) => {
        const fields = [r.name, r.cardNo, r.destination, r.purpose];
        return fields.some((f) => f?.toLowerCase().includes(searchQuery));
      });

  // Bulk approve/reject for the three tabs backed by a shared table
  // component. Each row already knows which endpoint its own status maps
  // to (advances are two-stage: HR then Accounts), so a bulk action is
  // just N per-row calls run together — same fallback path Overtime uses
  // when its rows don't share a batch id.
  const requisitionSelected = filteredRequisitionRows.filter((r) => selectedKeys.includes(r.id));
  const longTermAdvanceSelected = filteredLongTermAdvanceRows.filter((r) =>
    selectedKeys.includes(r.id),
  );
  const shortTermAdvanceSelected = filteredShortTermAdvanceRows.filter((r) =>
    selectedKeys.includes(r.id),
  );
  const bulkSelectedCount =
    kind === 'REQUISITIONS'
      ? requisitionSelected.length
      : kind === 'LONG_TERM_ADVANCES'
        ? longTermAdvanceSelected.length
        : kind === 'SHORT_TERM_ADVANCES'
          ? shortTermAdvanceSelected.length
          : 0;
  const bulkSelectionSupported =
    kind === 'REQUISITIONS' || kind === 'LONG_TERM_ADVANCES' || kind === 'SHORT_TERM_ADVANCES';

  const settleBulk = () => {
    setSelectedKeys([]);
    // Refreshes this page's own list (now sourced from the composite).
    void queryClient.invalidateQueries({ queryKey: ['bff', 'approvals', 'inbox'] });
    // Also refresh the dedicated module pages (/admin/employee-requisition,
    // /payroll/advances, /payroll/short-term-advances) that still read
    // these query keys directly.
    if (kind === 'REQUISITIONS') void queryClient.invalidateQueries({ queryKey: ['requisitions'] });
    if (kind === 'LONG_TERM_ADVANCES')
      void queryClient.invalidateQueries({ queryKey: ['payroll', 'permanent-advances'] });
    if (kind === 'SHORT_TERM_ADVANCES')
      void queryClient.invalidateQueries({ queryKey: ['payroll', 'short-term-advances'] });
  };

  const bulkApproveMutation = useMutation({
    mutationFn: async () => {
      if (kind === 'REQUISITIONS') {
        await Promise.all(requisitionSelected.map((r) => requisitionApi.approve(r.id)));
      } else if (kind === 'LONG_TERM_ADVANCES') {
        await Promise.all(
          longTermAdvanceSelected.map((r) => {
            switch (resolveAdvanceApprovalStage(r)) {
              case 'MANAGER_APPROVAL':
                return permanentAdvancesApi.managerApprove(r.id);
              case 'HR_APPROVAL':
                return permanentAdvancesApi.hrApprove(r.id);
              case 'ACCOUNTS_APPROVAL':
                return permanentAdvancesApi.accountsApprove(r.id);
              default:
                return Promise.reject(new Error('Unknown approval stage'));
            }
          }),
        );
      } else if (kind === 'SHORT_TERM_ADVANCES') {
        await Promise.all(
          shortTermAdvanceSelected.map((r) => {
            switch (resolveAdvanceApprovalStage(r)) {
              case 'MANAGER_APPROVAL':
                return shortTermAdvancesApi.managerApprove(r.id);
              case 'HR_APPROVAL':
                return shortTermAdvancesApi.hrApprove(r.id);
              case 'ACCOUNTS_APPROVAL':
                return shortTermAdvancesApi.accountsApprove(r.id);
              default:
                return Promise.reject(new Error('Unknown approval stage'));
            }
          }),
        );
      }
    },
    onSuccess: () => message.success(`Approved ${bulkSelectedCount}`),
    onError: (err) => message.error(getErrorMessage(err)),
    onSettled: settleBulk,
  });

  const bulkRejectMutation = useMutation({
    mutationFn: async (reason: string) => {
      if (kind === 'REQUISITIONS') {
        await Promise.all(
          requisitionSelected.map((r) => requisitionApi.reject(r.id, { reason })),
        );
      } else if (kind === 'LONG_TERM_ADVANCES') {
        await Promise.all(
          longTermAdvanceSelected.map((r) => permanentAdvancesApi.reject(r.id, { reason })),
        );
      } else if (kind === 'SHORT_TERM_ADVANCES') {
        await Promise.all(
          shortTermAdvanceSelected.map((r) => shortTermAdvancesApi.reject(r.id, { reason })),
        );
      }
    },
    onSuccess: () => message.success(`Rejected ${bulkSelectedCount}`),
    onError: (err) => message.error(getErrorMessage(err)),
    onSettled: settleBulk,
  });

  const shortLeaveRows: ShortLeaveInboxRow[] = data?.shortLeave ?? [];
  const counts = {
    LEAVES: data?.counts.leaves ?? 0,
    SHORT_LEAVE: data?.counts.shortLeave ?? shortLeaveRows.length,
    OVERTIME: data?.counts.overtime ?? 0,
    REGULARIZATION: data?.counts.regularization ?? 0,
    // Composite count when present; falls back to the row count so the
    // badge still works if `erroredSources` dropped that slice.
    REQUISITIONS: data?.counts.requisitions ?? requisitionRows.length,
    LONG_TERM_ADVANCES: data?.counts.permanentAdvances ?? longTermAdvanceRows.length,
    SHORT_TERM_ADVANCES: data?.counts.shortTermAdvances ?? shortTermAdvanceRows.length,
    TOUR_REQUESTS: data?.counts.travelRequests ?? tourRequestRows.length,
  };
  const total =
    counts.LEAVES +
    counts.SHORT_LEAVE +
    counts.OVERTIME +
    counts.REGULARIZATION +
    counts.REQUISITIONS +
    counts.LONG_TERM_ADVANCES +
    counts.SHORT_TERM_ADVANCES +
    counts.TOUR_REQUESTS;
  const loading = inboxQuery.isLoading;

  return (
    <div
      className="no-scrollbar"
      style={{
        display: 'flex',
        flexDirection: 'column',
        gap: 'var(--app-space-lg)',
        // Full width — the AppShell's <main> already supplies the page gutter.
        paddingBottom: 32,
        width: '100%',
        height: '100%',
        overflowY: 'auto',
      }}
    >
      <PageHeader
        eyebrow="My Inbox"
        title="Approvals"
        description="Leave, overtime, regularization, and requisition requests waiting on you."
        actions={
          <Space>
            <Link to="/inbox/history">
              <Button icon={<History size={14} />}>History</Button>
            </Link>
            <Tooltip title="Refresh">
              <Button
                icon={<RefreshCcw size={14} strokeWidth={1.75} />}
                onClick={() => inboxQuery.refetch()}
                loading={inboxQuery.isFetching}
                aria-label="Refresh"
              />
            </Tooltip>
          </Space>
        }
      />

      {total === 0 && !loading ? (
        <Card
          styles={{
            body: {
              padding: 0,
              minHeight: 320,
              display: 'grid',
              placeItems: 'center',
            },
          }}
        >
          <EmptyState
            icon={InboxIcon}
            title="Inbox zero"
            description="Nothing's waiting on you right now. Approval rows will appear here as your team submits leaves, overtime, regularizations, and requisitions."
          />
        </Card>
      ) : (
        <>
          <KpiGrid minColumnWidth={150} gap={12} className="inbox-summary-grid">
            <StatCard
              className="inbox-summary-card"
              styles={SUMMARY_CARD_STYLES}
              variant="accent-radial"
              tone="warning"
              accentCorner="tr"
              icon={CheckSquare}
              label="To review"
              value={`${total}`}
              delta={total === 1 ? 'request' : 'requests across all queues'}
            />
            <StatCard
              className="inbox-summary-card"
              styles={SUMMARY_CARD_STYLES}
              variant="plain"
              tone="info"
              icon={Plane}
              label="Leaves"
              value={`${counts.LEAVES}`}
              delta={
                <DecisionBreakdown
                  approved={leavesApproved}
                  rejected={leavesRejected}
                />
              }
            />
            <StatCard
              className="inbox-summary-card"
              styles={SUMMARY_CARD_STYLES}
              variant="plain"
              tone="info"
              icon={Timer}
              label="Short leave"
              value={`${counts.SHORT_LEAVE}`}
              delta={counts.SHORT_LEAVE === 1 ? 'request in review' : 'requests in review'}
            />
            <StatCard
              className="inbox-summary-card"
              styles={SUMMARY_CARD_STYLES}
              variant="plain"
              tone="primary"
              icon={Sparkles}
              label="Overtime"
              value={`${counts.OVERTIME}`}
              delta={
                <DecisionBreakdown
                  approved={overtimeApproved}
                  rejected={overtimeRejected}
                />
              }
            />
            <StatCard
              className="inbox-summary-card"
              styles={SUMMARY_CARD_STYLES}
              variant="plain"
              tone="success"
              icon={Stamp}
              label="Regularization"
              value={`${counts.REGULARIZATION}`}
              delta={
                <DecisionBreakdown
                  approved={regularizationApproved}
                  rejected={regularizationRejected}
                />
              }
            />
            <StatCard
              className="inbox-summary-card"
              styles={SUMMARY_CARD_STYLES}
              variant="plain"
              tone="neutral"
              icon={ClipboardCheck}
              label="Requisitions"
              value={`${counts.REQUISITIONS}`}
              delta={
                <DecisionBreakdown
                  approved={requisitionsApproved}
                  rejected={requisitionsRejected}
                />
              }
            />
            <StatCard
              className="inbox-summary-card"
              styles={SUMMARY_CARD_STYLES}
              variant="plain"
              tone="warning"
              icon={Banknote}
              label="Long-term advances"
              value={`${counts.LONG_TERM_ADVANCES}`}
              delta={counts.LONG_TERM_ADVANCES === 1 ? 'request in review' : 'requests in review'}
            />
            <StatCard
              className="inbox-summary-card"
              styles={SUMMARY_CARD_STYLES}
              variant="plain"
              tone="warning"
              icon={Wallet}
              label="Short-term advances"
              value={`${counts.SHORT_TERM_ADVANCES}`}
              delta={counts.SHORT_TERM_ADVANCES === 1 ? 'batch in review' : 'batches in review'}
            />
            <StatCard
              className="inbox-summary-card"
              styles={SUMMARY_CARD_STYLES}
              variant="plain"
              tone="info"
              icon={MapPin}
              label="Travel requests"
              value={`${counts.TOUR_REQUESTS}`}
              delta={counts.TOUR_REQUESTS === 1 ? 'request' : 'requests'}
            />
          </KpiGrid>

          <div>
            <div className="no-scrollbar" style={{ overflowX: 'auto', marginBottom: 12 }}>
                <Segmented
                  value={kind}
                  onChange={(v) => changeKind(v as InboxKind)}
                  style={{ width: 'max-content' }}
                  options={[
                    {
                      value: 'LEAVES',
                      label: <SegmentLabel icon={Plane} text="Leaves" count={counts.LEAVES} />,
                    },
                    {
                      value: 'SHORT_LEAVE',
                      label: (
                        <SegmentLabel icon={Timer} text="Short leave" count={counts.SHORT_LEAVE} />
                      ),
                    },
                    {
                      value: 'OVERTIME',
                      label: (
                        <SegmentLabel icon={Sparkles} text="Overtime" count={counts.OVERTIME} />
                      ),
                    },
                    {
                      value: 'REGULARIZATION',
                      label: (
                        <SegmentLabel
                          icon={Stamp}
                          text="Regularization"
                          count={counts.REGULARIZATION}
                        />
                      ),
                    },
                    {
                      value: 'REQUISITIONS',
                      label: (
                        <SegmentLabel
                          icon={ClipboardCheck}
                          text="Requisitions"
                          count={counts.REQUISITIONS}
                        />
                      ),
                    },
                    {
                      value: 'LONG_TERM_ADVANCES',
                      label: (
                        <SegmentLabel
                          icon={Banknote}
                          text="Long-term advances"
                          count={counts.LONG_TERM_ADVANCES}
                        />
                      ),
                    },
                    {
                      value: 'SHORT_TERM_ADVANCES',
                      label: (
                        <SegmentLabel
                          icon={Wallet}
                          text="Short-term advances"
                          count={counts.SHORT_TERM_ADVANCES}
                        />
                      ),
                    },
                    {
                      value: 'TOUR_REQUESTS',
                      label: (
                        <SegmentLabel
                          icon={MapPin}
                          text="Travel requests"
                          count={counts.TOUR_REQUESTS}
                        />
                      ),
                    },
                  ]}
                />
            </div>
            <div>
              {bulkSelectionSupported && bulkSelectedCount > 0 ? (
                <Card
                  size="small"
                  styles={{ body: { padding: '10px 14px' } }}
                  style={{ marginBottom: 12 }}
                >
                  <div
                    style={{
                      display: 'flex',
                      alignItems: 'center',
                      justifyContent: 'space-between',
                    }}
                  >
                    <span style={{ fontSize: 13 }}>{bulkSelectedCount} selected</span>
                    <Space>
                      <Button onClick={() => setSelectedKeys([])}>Clear</Button>
                      <Button
                        danger
                        icon={<XCircle size={12} />}
                        onClick={() => setBatchRejectOpen(true)}
                        loading={bulkRejectMutation.isPending}
                      >
                        Reject
                      </Button>
                      <Button
                        type="primary"
                        icon={<CheckCircle2 size={12} />}
                        onClick={() => bulkApproveMutation.mutate()}
                        loading={bulkApproveMutation.isPending}
                      >
                        Approve
                      </Button>
                    </Space>
                  </div>
                </Card>
              ) : null}
              {missingLink ? (
                <Alert
                  type="info"
                  showIcon
                  closable
                  onClose={clearOpen}
                  style={{ marginBottom: 12 }}
                  title="That request isn't waiting on you any more"
                  description={
                    <>
                      It may already have been approved or rejected, or it moved to the next
                      stage. Your decisions are in{' '}
                      <Link to="/inbox/history">History</Link>.
                    </>
                  }
                />
              ) : null}
              {kind === 'LEAVES' ? (
                <LeavesInbox
                  rows={data?.leaves ?? []}
                  loading={inboxQuery.isLoading}
                  error={inboxQuery.error}
                  openId={openId}
                  onOpened={clearOpen}
                />
              ) : kind === 'SHORT_LEAVE' ? (
                <ShortLeaveInbox
                  rows={shortLeaveRows}
                  loading={inboxQuery.isLoading}
                  error={inboxQuery.error}
                  openId={openId}
                  onOpened={clearOpen}
                />
              ) : kind === 'OVERTIME' ? (
                <OvertimeInbox
                  rows={data?.overtime ?? []}
                  loading={inboxQuery.isLoading}
                  error={inboxQuery.error}
                  openId={openId}
                  onOpened={clearOpen}
                />
              ) : kind === 'REGULARIZATION' ? (
                <RegularizationInbox
                  rows={data?.regularization ?? []}
                  loading={inboxQuery.isLoading}
                  error={inboxQuery.error}
                  openId={openId}
                  onOpened={clearOpen}
                />
              ) : kind === 'REQUISITIONS' ? (
                requisitionRows.length === 0 ? (
                  <RequisitionTable
                    data={requisitionRows}
                    loading={inboxQuery.isFetching}
                    departments={departmentsQuery.data}
                    sections={sectionsQuery.data}
                    designations={designationsQuery.data}
                    grades={gradesQuery.data}
                    onEdit={noopEdit}
                    hideCancel
                    statusLabels={RAW_REQUISITION_STATUS}
                    inboxLayout
                  />
                ) : (
                  <>
                    <FilterBar
                      search={{
                        value: search,
                        onChange: setSearch,
                        placeholder: 'Search by request ID',
                      }}
                      filters={
                        <>
                          <Select<number>
                            allowClear
                            showSearch
                            optionFilterProp="label"
                            placeholder="All departments"
                            style={{ width: 200 }}
                            value={requisitionDepartmentId}
                            onChange={(v) => setRequisitionDepartmentId(v ?? undefined)}
                            loading={departmentsQuery.isLoading}
                            options={(departmentsQuery.data ?? []).map((d) => ({
                              value: d.id,
                              label: d.name,
                            }))}
                          />
                          <Select<RequisitionType>
                            allowClear
                            placeholder="All types"
                            style={{ width: 150 }}
                            value={requisitionType}
                            onChange={(v) => setRequisitionType(v ?? undefined)}
                            options={(Object.keys(REQUISITION_TYPE_LABEL) as RequisitionType[]).map(
                              (t) => ({ value: t, label: REQUISITION_TYPE_LABEL[t] }),
                            )}
                          />
                        </>
                      }
                      onReset={
                        search || requisitionType != null || requisitionDepartmentId != null
                          ? () => {
                              setSearch('');
                              setRequisitionType(undefined);
                              setRequisitionDepartmentId(undefined);
                            }
                          : undefined
                      }
                      style={{ marginBottom: 12 }}
                    />
                    {requisitionSearch.error ? (
                      <Alert
                        type="error"
                        showIcon
                        style={{ marginBottom: 12 }}
                        title="Search failed"
                        description={getErrorMessage(requisitionSearch.error)}
                      />
                    ) : null}
                    <RequisitionTable
                      data={filteredRequisitionRows}
                      loading={inboxQuery.isFetching || requisitionSearch.fetching}
                      departments={departmentsQuery.data}
                      sections={sectionsQuery.data}
                      designations={designationsQuery.data}
                      grades={gradesQuery.data}
                      onEdit={noopEdit}
                      onDetails={setViewingRequisition}
                      hideCancel
                      statusLabels={RAW_REQUISITION_STATUS}
                      inboxLayout
                      rowSelection={{
                        selectedRowKeys: selectedKeys,
                        onChange: setSelectedKeys,
                      }}
                      pagination={{
                        defaultPageSize: 20,
                        pageSizeOptions: [10, 20, 50, 100],
                        showSizeChanger: true,
                        showTotal: (total, [from, to]) => `${from}–${to} of ${total}`,
                      }}
                    />
                  </>
                )
              ) : kind === 'LONG_TERM_ADVANCES' ? (
                longTermAdvanceRows.length === 0 ? (
                  <EmptyState
                    icon={Banknote}
                    title="Nothing pending"
                    description="Long-term salary advances waiting on HR or Accounts will show up here."
                  />
                ) : (
                  <>
                    <FilterBar
                      search={{
                        value: search,
                        onChange: setSearch,
                        placeholder: 'Search by request ID, code or employee',
                      }}
                      style={{ marginBottom: 12 }}
                    />
                    {longTermAdvanceSearch.error ? (
                      <Alert
                        type="error"
                        showIcon
                        style={{ marginBottom: 12 }}
                        title="Search failed"
                        description={getErrorMessage(longTermAdvanceSearch.error)}
                      />
                    ) : null}
                    <PermanentAdvanceTable
                      data={filteredLongTermAdvanceRows}
                      loading={inboxQuery.isFetching || longTermAdvanceSearch.fetching}
                      inboxLayout
                      employees={longTermAdvanceEmployees}
                      showEmployee
                      showActions
                      // Status shows the request's stored status verbatim;
                      // the Stage column already says who it's waiting on.
                      statusLabels={RAW_ADVANCE_STATUS}
                      onDetails={setViewingPermanentAdvance}
                      rowSelection={{
                        selectedRowKeys: selectedKeys,
                        onChange: setSelectedKeys,
                      }}
                    />
                  </>
                )
              ) : kind === 'SHORT_TERM_ADVANCES' ? (
                shortTermAdvanceRows.length === 0 ? (
                  <EmptyState
                    icon={Wallet}
                    title="Nothing pending"
                    description="Short-term advance batches waiting on HR or Accounts will show up here."
                  />
                ) : (
                  <>
                    <FilterBar
                      search={{
                        value: search,
                        onChange: setSearch,
                        placeholder: 'Search by Request ID or request code',
                      }}
                      style={{ marginBottom: 12 }}
                    />
                    {shortTermAdvanceSearch.error ? (
                      <Alert
                        type="error"
                        showIcon
                        style={{ marginBottom: 12 }}
                        title="Search failed"
                        description={getErrorMessage(shortTermAdvanceSearch.error)}
                      />
                    ) : null}
                    <ShortTermAdvanceBatchTable
                      data={filteredShortTermAdvanceRows}
                      loading={inboxQuery.isFetching || shortTermAdvanceSearch.fetching}
                      showActions
                      statusLabels={RAW_ADVANCE_STATUS}
                      inboxLayout
                      onDetails={setViewingShortTermAdvance}
                      rowSelection={{
                        selectedRowKeys: selectedKeys,
                        onChange: setSelectedKeys,
                      }}
                    />
                  </>
                )
              ) : tourRequestRows.length === 0 ? (
                <EmptyState
                  icon={MapPin}
                  title="Nothing pending"
                  description="Travel requests will show up here."
                />
              ) : (
                <>
                  <FilterBar
                    search={{
                      value: search,
                      onChange: setSearch,
                      placeholder: 'Search employee, destination, purpose…',
                    }}
                    style={{ marginBottom: 12 }}
                  />
                  <FactoryTourTable
                    data={filteredTourRequestRows}
                    loading={inboxQuery.isFetching}
                    departments={departmentsQuery.data}
                    sections={sectionsQuery.data}
                    designations={designationsQuery.data}
                    grades={gradesQuery.data}
                    onDetails={setViewingFactoryTour}
                  />
                </>
              )}
            </div>
          </div>
          <RejectReasonModal
            open={batchRejectOpen}
            title={`Reject ${bulkSelectedCount} request(s)?`}
            onCancel={() => setBatchRejectOpen(false)}
            onConfirm={async (reason) => {
              await bulkRejectMutation.mutateAsync(reason);
              setBatchRejectOpen(false);
            }}
          />
          <RequisitionDetailDrawer
            row={viewingRequisition}
            onClose={() => setViewingRequisition(null)}
            onEdit={noopEdit}
            departments={departmentsQuery.data}
            sections={sectionsQuery.data}
            designations={designationsQuery.data}
            grades={gradesQuery.data}
            hideCancel
            approvalView
          />
          <PermanentAdvanceDetailDrawer
            row={viewingPermanentAdvance}
            onClose={() => setViewingPermanentAdvance(null)}
          />
          <ShortTermAdvanceBatchDetailDrawer
            row={viewingShortTermAdvance}
            onClose={() => setViewingShortTermAdvance(null)}
          />
          <FactoryTourDetailDrawer
            row={viewingFactoryTour}
            onClose={() => setViewingFactoryTour(null)}
            departments={departmentsQuery.data}
            sections={sectionsQuery.data}
            designations={designationsQuery.data}
            grades={gradesQuery.data}
          />
        </>
      )}
    </div>
  );
}
