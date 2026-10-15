// /inbox/history — "approvals I have decided" view (§12.2 BE).
//
// Sibling of the pending inbox at /inbox. Lists the rows the caller closed
// (APPROVED or REJECTED) at any stage of any request type, in a date range.
// Server enforces a 366-day max window; the FE defaults to the last 30 days.
//
// URL-driven (date range, type, decision, page) so refresh/share/back work.

import { useMemo } from 'react';
import { createFileRoute, Link, useNavigate, useSearch } from '@tanstack/react-router';
import { useQuery } from '@tanstack/react-query';
import {
  Alert,
  Button,
  DatePicker,
  Segmented,
  Select,
  Table,
  Tag,
  Typography,
  type TableProps,
} from 'antd';
import dayjs from 'dayjs';
import { History, Inbox as InboxIcon } from 'lucide-react';

import {
  DateRangeBadge,
  EmptyState,
  FilterBar,
  PageHeader,
  PersonCell,
  RequestState,
  Section,
  StatusPill,
} from '@/components';
import {
  APPROVAL_REQUEST_TYPES,
  approvalsBff,
  formatDateShort,
  formatDateTime,
  prettyLeaveCode,
  type ApprovalDecisionRow,
  type ApprovalRequestType,
} from '@/attendance';
import { numOrUndef, strOrUndef } from '@/attendance/admin';
import { forbiddenOr } from '@/lib';

const { Text } = Typography;
const { RangePicker } = DatePicker;

// Every request type now comes from one place. /bff/approvals/history fans
// out to attendance, payroll and employee, scopes each source to the caller,
// and honours `type` + `decision` server-side — a `type` is routed only to
// its owning service. Before that existed this file kept a parallel branch
// that read each module's own list({status}) and merged client-side; those
// rows were org-wide, not "decisions I made", and the view said so in an
// Alert. Both the branch and the caveat are gone.

type HistorySearch = {
  from?: string;
  to?: string;
  type?: ApprovalRequestType;
  decision?: 'APPROVED' | 'REJECTED';
  page?: number;
  size?: number;
};

function isRequestType(v: unknown): v is ApprovalRequestType {
  return APPROVAL_REQUEST_TYPES.includes(v as ApprovalRequestType);
}

function isDecision(v: unknown): v is 'APPROVED' | 'REJECTED' {
  return v === 'APPROVED' || v === 'REJECTED';
}

export const Route = createFileRoute('/_app/inbox/history')({
  validateSearch: (search): HistorySearch => ({
    from: strOrUndef(search.from),
    to: strOrUndef(search.to),
    type: isRequestType(search.type) ? search.type : undefined,
    decision: isDecision(search.decision) ? search.decision : undefined,
    page: numOrUndef(search.page),
    size: numOrUndef(search.size),
  }),
  component: HistoryPage,
});

function HistoryPage() {
  const navigate = useNavigate({ from: '/inbox/history' });
  const search = useSearch({ from: '/_app/inbox/history' });

  // Default to last 30 days when the URL has no range. Server requires both
  // dates and clamps to 366 days.
  const today = dayjs();
  const fromDate = search.from ?? today.subtract(30, 'day').format('YYYY-MM-DD');
  const toDate = search.to ?? today.format('YYYY-MM-DD');
  const page = search.page ?? 1;
  const size = search.size ?? 20;

  const type = isRequestType(search.type) ? search.type : undefined;

  // `/approvals/history` is gateway-rewritten to the BFF composite, which
  // fans out to attendance, payroll and employee and answers `{ rows, total }`
  // — not a Spring page. `type` and `decision` are honoured server-side (a
  // `type` fans out only to its owning source), so they belong in the query
  // key. There is still no server paging, so the merged list is sliced below.
  const historyQuery = useQuery({
    queryKey: ['approvals', 'history', fromDate, toDate, type, search.decision],
    queryFn: ({ signal }) =>
      approvalsBff.history(
        { from: fromDate, to: toDate, type, decision: search.decision },
        signal,
      ),
    placeholderData: (prev) => prev,
  });

  const dirty =
    !!search.from || !!search.to || !!search.type || !!search.decision;

  const allRows = useMemo(
    () => historyQuery.data?.rows ?? [],
    [historyQuery.data],
  );
  const total = allRows.length;
  const rows = useMemo(
    () => allRows.slice((page - 1) * size, page * size),
    [allRows, page, size],
  );

  const erroredSources = historyQuery.data?.erroredSources ?? [];

  const columns: NonNullable<TableProps<ApprovalDecisionRow>['columns']> = [
    {
      title: 'Decided',
      dataIndex: 'decidedAt',
      width: 170,
      render: (v: string) => (
        <Text style={{ fontVariantNumeric: 'tabular-nums' }}>{formatDateTime(v)}</Text>
      ),
    },
    {
      title: 'Type',
      dataIndex: 'type',
      width: 120,
      render: (v: ApprovalDecisionRow['type'], row) => (
        <div style={{ display: 'flex', flexDirection: 'column', gap: 2 }}>
          <Tag color={typeColor(v)} style={{ marginRight: 0 }}>
            {typeLabel(v)}
          </Tag>
          <Text type="secondary" style={{ fontSize: 11 }}>
            {row.requestCode}
          </Text>
        </div>
      ),
    },
    {
      title: 'Employee',
      dataIndex: 'fullName',
      render: (_v, row) =>
        row.fullName ? (
          <PersonCell
            person={{
              fullName: row.fullName,
              employeeCode: row.employeeCode,
            }}
            size="compact"
            to={`/people/${row.employeeId}`}
          />
        ) : (
          <Text type="secondary">#{row.employeeId}</Text>
        ),
    },
    {
      title: 'Date(s)',
      width: 200,
      render: (_v, row) => renderDate(row),
    },
    {
      title: 'Detail',
      render: (_v, row) => renderDetail(row),
    },
    {
      title: 'Stage',
      dataIndex: 'stageRole',
      width: 110,
      render: (v: string | null, row) => (
        <div>
          <Tag>{v ?? `#${row.stageNo}`}</Tag>
        </div>
      ),
    },
    {
      title: 'Outcome',
      dataIndex: 'decision',
      width: 120,
      render: (v: 'APPROVED' | 'REJECTED') => (
        <StatusPill tone={v === 'APPROVED' ? 'success' : 'error'}>
          {v === 'APPROVED' ? 'Approved' : 'Rejected'}
        </StatusPill>
      ),
    },
  ];

  return (
    <div
      style={{
        display: 'flex',
        flexDirection: 'column',
        gap: 'var(--app-space-lg)',
        width: '100%',
      }}
    >
      <PageHeader
        eyebrow="My Inbox"
        title="Approval history"
        description="Decisions you've closed — leave, overtime, regularization, advances, requisitions and travel."
        actions={
          <Link to="/inbox">
            <Button>Back to pending</Button>
          </Link>
        }
      />

      <Section
        title={total === 1 ? '1 decision' : `${total} decisions`}
        actions={
          <FilterBar
            filters={
              <>
                <RangePicker
                  value={[dayjs(fromDate), dayjs(toDate)]}
                  format="DD MMM YYYY"
                  allowClear={false}
                  onChange={(v) => {
                    if (!v || !v[0] || !v[1]) return;
                    navigate({
                      search: (prev) => ({
                        ...prev,
                        from: v[0]!.format('YYYY-MM-DD'),
                        to: v[1]!.format('YYYY-MM-DD'),
                        page: 1,
                      }),
                      replace: true,
                    });
                  }}
                />
                <Select<ApprovalRequestType | 'ALL'>
                  value={search.type ?? 'ALL'}
                  onChange={(v) =>
                    navigate({
                      search: (prev) => ({
                        ...prev,
                        type: v === 'ALL' ? undefined : v,
                        page: 1,
                      }),
                      replace: true,
                    })
                  }
                  style={{ width: 220 }}
                  options={[
                    { value: 'ALL', label: 'All types' },
                    ...APPROVAL_REQUEST_TYPES.map((t) => ({
                      value: t,
                      label: typeLabel(t),
                    })),
                  ]}
                />
                <Segmented
                  value={search.decision ?? 'ALL'}
                  onChange={(v) =>
                    navigate({
                      search: (prev) => ({
                        ...prev,
                        decision: v === 'ALL' ? undefined : (v as 'APPROVED' | 'REJECTED'),
                        page: 1,
                      }),
                      replace: true,
                    })
                  }
                  options={[
                    { value: 'ALL', label: 'All' },
                    { value: 'APPROVED', label: 'Approved' },
                    { value: 'REJECTED', label: 'Rejected' },
                  ]}
                />
              </>
            }
            onReset={
              dirty
                ? () =>
                    navigate({
                      search: () => ({}),
                      replace: true,
                    })
                : undefined
            }
          />
        }
      >
        <>
          {erroredSources.length > 0 ? (
            // The composite soft-fails a source to an empty bucket rather
            // than failing the call, so without this the rows are just
            // quietly missing.
            <Alert
              type="warning"
              showIcon
              style={{ marginBottom: 12 }}
              title={`Some decisions couldn't be loaded (${erroredSources.join(', ')})`}
              description="This list is incomplete. Retry in a moment — the other sources are shown."
            />
          ) : null}
          <RequestState
            loading={historyQuery.isLoading}
            error={historyQuery.error}
            empty={!historyQuery.isLoading && rows.length === 0}
            errorTitle={forbiddenOr(
              historyQuery.error,
              "You don't have permission to view approval history",
              "Couldn't load approval history",
            )}
            emptyFallback={
              <EmptyState
                icon={search.decision || search.type ? InboxIcon : History}
                title={
                  search.decision || search.type
                    ? 'No matches in this window'
                    : 'No decisions in this window'
                }
                description={
                  search.decision || search.type
                    ? 'Try widening the date range or clearing filters.'
                    : "Approve or reject something from your inbox and it'll show up here."
                }
              />
            }
          >
            <Table<ApprovalDecisionRow>
              rowKey={(row) => `${row.type}:${row.requestId}:${row.stageNo}`}
              dataSource={rows}
              columns={columns}
              size="middle"
              scroll={{ x: 'max-content' }}
              pagination={{
                current: page,
                pageSize: size,
                total,
                showSizeChanger: true,
                pageSizeOptions: [20, 50, 100],
                showTotal: (t, [from, to]) => `${from}–${to} of ${t}`,
                onChange: (p, s) =>
                  navigate({
                    search: (prev) => ({ ...prev, page: p, size: s }),
                    replace: true,
                  }),
              }}
            />
          </RequestState>
        </>
      </Section>
    </div>
  );
}

/** One place for the label + tag colour of every request type the composite
 *  can return. Keyed by the wire value so a new backend type shows up as an
 *  obvious gap rather than a crash. */
const TYPE_META: Record<ApprovalRequestType, { label: string; color: string }> = {
  LEAVE: { label: 'Leave', color: 'blue' },
  SHORT_LEAVE: { label: 'Short leave', color: 'cyan' },
  OVERTIME: { label: 'Overtime', color: 'gold' },
  REGULARIZATION: { label: 'Regularize', color: 'green' },
  PERMANENT_ADVANCE: { label: 'Long-term advance', color: 'purple' },
  SHORT_TERM_ADVANCE: { label: 'Short-term advance', color: 'magenta' },
  REQUISITION_NEW_HIRE: { label: 'Requisition — new hire', color: 'geekblue' },
  REQUISITION_REPLACEMENT: { label: 'Requisition — replacement', color: 'geekblue' },
  TRAVEL_REQUEST: { label: 'Travel request', color: 'volcano' },
  TRAVEL_ADVANCE: { label: 'Travel advance', color: 'orange' },
  TRAVEL_SETTLEMENT: { label: 'Travel settlement', color: 'red' },
};

function typeColor(t: ApprovalDecisionRow['type']): string {
  return TYPE_META[t]?.color ?? 'default';
}

function typeLabel(t: ApprovalDecisionRow['type']): string {
  return TYPE_META[t]?.label ?? t;
}

function renderDate(row: ApprovalDecisionRow) {
  if (!row.startDate) return <Text type="secondary">—</Text>;
  if (row.endDate && row.endDate !== row.startDate) {
    return <DateRangeBadge start={row.startDate} end={row.endDate} format="short" />;
  }
  return <Text style={{ fontVariantNumeric: 'tabular-nums' }}>{formatDateShort(row.startDate)}</Text>;
}

function renderDetail(row: ApprovalDecisionRow) {
  const bits: string[] = [];
  if (row.type === 'LEAVE') {
    if (row.leaveTypeCode) bits.push(prettyLeaveCode(row.leaveTypeCode));
    if (row.days != null) {
      bits.push(`${row.days} day${row.days === 1 ? '' : 's'}`);
    }
  }
  if (row.note) bits.push(`“${row.note}”`);
  else if (row.reason) bits.push(row.reason);
  return bits.length === 0 ? (
    <Text type="secondary">—</Text>
  ) : (
    <Text style={{ fontSize: 13 }}>{bits.join(' · ')}</Text>
  );
}
