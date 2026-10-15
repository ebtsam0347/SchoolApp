// /people/queues/hr-requisition-approval — HR queue for hiring requisitions.
// Same page as /admin/employee-requisition (KPIs, status/type/department
// filters, detail drawer), minus the "New requisition" action — this is
// HR's review/track surface, not a place to raise a request.
//
// Flow recap: DRAFT -> PENDING_APPROVAL -> APPROVED -> FULFILLED, with
// REJECTED / CANCELLED as terminal off-ramps.

import { useState } from 'react';
import { useQuery } from '@tanstack/react-query';
import { createFileRoute, useNavigate, useSearch } from '@tanstack/react-router';
import { Button, Segmented, Select, Space, Tooltip } from 'antd';
import {
  Ban,
  ClipboardList,
  FileCheck2,
  Lock,
  RefreshCcw,
  Send,
  XCircle,
} from 'lucide-react';

import {
  EmptyState,
  KpiGrid,
  PageHeader,
  RequestState,
  Section,
  StatCard,
} from '@/components';
import { catalogsApi, peopleBff } from '@/people';
import {
  REQUISITION_STATUS_LABEL,
  REQUISITION_TYPE_LABEL,
  RequisitionDetailDrawer,
  RequisitionHiresModal,
  RequisitionTable,
  requisitionApi,
  type RequisitionDto,
  type RequisitionStatus,
  type RequisitionType,
} from '@/requisition';

type Search = {
  status?: RequisitionStatus;
  type?: RequisitionType;
  departmentId?: number;
  page?: number;
  size?: number;
};

const STATUS_VALUES: RequisitionStatus[] = [
  'DRAFT',
  'PENDING_APPROVAL',
  'APPROVED',
  'REJECTED',
  'FULFILLED',
  'CANCELLED',
];
// Draft (a requester's own unsubmitted scratch state) and Fulfilled
// (closed-out history) aren't segmented tabs — same as the Hiring
// Requisitions page. A direct `?status=DRAFT`/`?status=FULFILLED` link
// still resolves.
const HIDDEN_STATUSES: RequisitionStatus[] = ['DRAFT', 'FULFILLED'];
const STATUS_FILTER_OPTIONS: { value: RequisitionStatus | 'ALL'; label: string }[] = [
  { value: 'ALL', label: 'All' },
  ...STATUS_VALUES.filter((s) => !HIDDEN_STATUSES.includes(s)).map((s) => ({
    value: s,
    label: REQUISITION_STATUS_LABEL[s],
  })),
];
const TYPE_VALUES: RequisitionType[] = ['NEW_HIRE', 'REPLACEMENT'];

function isStatus(v: unknown): v is RequisitionStatus {
  return typeof v === 'string' && (STATUS_VALUES as string[]).includes(v);
}
function isType(v: unknown): v is RequisitionType {
  return typeof v === 'string' && (TYPE_VALUES as string[]).includes(v);
}
function numOrUndef(v: unknown): number | undefined {
  const n = Number(v);
  return typeof v !== 'undefined' && !Number.isNaN(n) ? n : undefined;
}

export const Route = createFileRoute('/_app/people/queues/hr-requisition-approval')({
  validateSearch: (search): Search => ({
    status: isStatus(search.status) ? search.status : undefined,
    type: isType(search.type) ? search.type : undefined,
    departmentId: numOrUndef(search.departmentId),
    page: numOrUndef(search.page),
    size: numOrUndef(search.size),
  }),
  component: RouteComponent,
});

function RouteComponent() {
  const navigate = useNavigate({ from: '/people/queues/hr-requisition-approval' });
  const search = useSearch({ from: '/_app/people/queues/hr-requisition-approval' });
  const [viewingHires, setViewingHires] = useState<RequisitionDto | null>(null);
  const [viewingDetail, setViewingDetail] = useState<RequisitionDto | null>(null);

  const page = search.page ?? 1;
  const size = search.size ?? 20;

  // Shares the ['account', 'profile'] cache key with the Sidebar's employee
  // check, so this doesn't double-hit /bff/account/profile.
  const profileQuery = useQuery({
    queryKey: ['account', 'profile'],
    queryFn: ({ signal }) => peopleBff.accountProfile(signal),
    staleTime: 60_000,
    retry: false,
  });
  const hasAccess = !!profileQuery.data?.employee;
  const accessResolved = profileQuery.isFetched;

  const listQuery = useQuery({
    queryKey: ['requisitions', 'hr-approval-queue', search],
    queryFn: ({ signal }) =>
      requisitionApi.list(
        {
          status: search.status,
          type: search.type,
          departmentId: search.departmentId,
          page: page - 1,
          size,
        },
        signal,
      ),
    enabled: hasAccess,
  });

  const draftCount = useQuery({
    queryKey: ['requisitions', 'count', 'DRAFT'],
    queryFn: ({ signal }) => requisitionApi.list({ status: 'DRAFT', size: 1 }, signal),
    staleTime: 30_000,
    select: (d) => d.totalElements,
    enabled: hasAccess,
  });
  const pendingCount = useQuery({
    queryKey: ['requisitions', 'count', 'PENDING_APPROVAL'],
    queryFn: ({ signal }) =>
      requisitionApi.list({ status: 'PENDING_APPROVAL', size: 1 }, signal),
    staleTime: 30_000,
    select: (d) => d.totalElements,
    enabled: hasAccess,
  });
  const approvedCount = useQuery({
    queryKey: ['requisitions', 'count', 'APPROVED'],
    queryFn: ({ signal }) => requisitionApi.list({ status: 'APPROVED', size: 1 }, signal),
    staleTime: 30_000,
    select: (d) => d.totalElements,
    enabled: hasAccess,
  });
  const rejectedCount = useQuery({
    queryKey: ['requisitions', 'count', 'REJECTED'],
    queryFn: ({ signal }) => requisitionApi.list({ status: 'REJECTED', size: 1 }, signal),
    staleTime: 30_000,
    select: (d) => d.totalElements,
    enabled: hasAccess,
  });
  const fulfilledCount = useQuery({
    queryKey: ['requisitions', 'count', 'FULFILLED'],
    queryFn: ({ signal }) => requisitionApi.list({ status: 'FULFILLED', size: 1 }, signal),
    staleTime: 30_000,
    select: (d) => d.totalElements,
    enabled: hasAccess,
  });
  const cancelledCount = useQuery({
    queryKey: ['requisitions', 'count', 'CANCELLED'],
    queryFn: ({ signal }) => requisitionApi.list({ status: 'CANCELLED', size: 1 }, signal),
    staleTime: 30_000,
    select: (d) => d.totalElements,
    enabled: hasAccess,
  });

  const departmentsQuery = useQuery({
    queryKey: ['catalog', 'departments'],
    queryFn: ({ signal }) => catalogsApi.departments(signal),
    staleTime: 5 * 60 * 1000,
    enabled: hasAccess,
  });
  const sectionsQuery = useQuery({
    queryKey: ['catalog', 'sections'],
    queryFn: ({ signal }) => catalogsApi.sections(undefined, signal),
    staleTime: 5 * 60 * 1000,
    enabled: hasAccess,
  });
  const designationsQuery = useQuery({
    queryKey: ['catalog', 'designations'],
    queryFn: ({ signal }) => catalogsApi.designations(signal),
    staleTime: 5 * 60 * 1000,
    enabled: hasAccess,
  });
  const gradesQuery = useQuery({
    queryKey: ['catalog', 'grades'],
    queryFn: ({ signal }) => catalogsApi.grades(signal),
    staleTime: 5 * 60 * 1000,
    enabled: hasAccess,
  });

  // Draft / Fulfilled are filtered client-side out of the shared "All"
  // queue — the backend list filter only takes a single `status` (no
  // exclusion). A direct `?status=DRAFT`/`?status=FULFILLED` link still
  // works since there's no reason to hard-block reading a specific record.
  const rows = (listQuery.data?.content ?? []).filter(
    (r) => r.status === search.status || !HIDDEN_STATUSES.includes(r.status),
  );
  const hiddenCount = (draftCount.data ?? 0) + (fulfilledCount.data ?? 0);
  const totalElements = search.status
    ? (listQuery.data?.totalElements ?? 0)
    : Math.max(0, (listQuery.data?.totalElements ?? 0) - hiddenCount);

  const hasActiveFilters = Boolean(
    search.status || search.type || search.departmentId,
  );

  const updateFilter = (patch: Partial<Search>) =>
    navigate({ search: (prev) => ({ ...prev, ...patch, page: 1 }), replace: true });

  const clearAll = () => navigate({ search: () => ({}), replace: true });

  // Edit only applies to DRAFT rows — but the shared table/drawer still
  // expect the prop, so hand them a no-op.
  const noopEdit = () => {};

  // Wait for the employee check to resolve before deciding — showing the
  // denial screen for a beat while profile is still loading would be a
  // false negative for genuine HR users.
  if (!accessResolved) return null;

  if (!hasAccess) {
    return (
      <div style={{ padding: '64px 24px', maxWidth: 480, margin: '0 auto' }}>
        <EmptyState
          icon={Lock}
          title="You don't have access to this page"
          description="HR requisition approvals are available to employees with a linked employee record."
        />
      </div>
    );
  }

  return (
    <div
      style={{
        display: 'flex',
        flexDirection: 'column',
        gap: 24,
        width: '100%',
      }}
    >
      <PageHeader
        eyebrow="People"
        title="HR Requisition Assign"
        description="Hiring requisitions and the stage each one has reached in its approval chain."
        actions={
          <Space>
            <Tooltip title="Refresh">
              <Button
                icon={<RefreshCcw size={14} strokeWidth={1.75} />}
                onClick={() => listQuery.refetch()}
                loading={listQuery.isFetching}
                aria-label="Refresh"
              />
            </Tooltip>
            <Button onClick={() => navigate({ to: '/people' })}>Back to People</Button>
          </Space>
        }
      />

      <KpiGrid minColumnWidth={200}>
        <StatCard
          variant="accent-radial"
          tone="warning"
          icon={Send}
          accentCorner="tr"
          label="Pending approval"
          value={`${pendingCount.data ?? 0}`}
          delta="awaiting a decision"
        />
        <StatCard
          variant="plain"
          tone="success"
          icon={FileCheck2}
          label="Approved"
          value={`${approvedCount.data ?? 0}`}
          delta="ready to be filled"
        />
        <StatCard
          variant="plain"
          tone={(rejectedCount.data ?? 0) > 0 ? 'error' : 'neutral'}
          icon={XCircle}
          label="Rejected"
          value={`${rejectedCount.data ?? 0}`}
        />
        <StatCard
          variant="plain"
          tone="neutral"
          icon={Ban}
          label="Cancelled"
          value={`${cancelledCount.data ?? 0}`}
        />
      </KpiGrid>

      <Section
        title="Requisitions"
        subtitle={`${totalElements.toLocaleString('en-PK')} total`}
        styles={{ root: { minWidth: 0 } }}
        actions={
          <Space size={12} wrap>
            <Segmented
              size="small"
              value={search.status ?? 'ALL'}
              onChange={(v) =>
                updateFilter({ status: v === 'ALL' ? undefined : (v as RequisitionStatus) })
              }
              options={STATUS_FILTER_OPTIONS}
            />
            <Space size={8} wrap>
              <Select<RequisitionType | undefined>
                allowClear
                placeholder="Type"
                style={{ width: 'clamp(110px, 40vw, 150px)' }}
                value={search.type}
                onChange={(v) => updateFilter({ type: v })}
                options={TYPE_VALUES.map((t) => ({
                  value: t,
                  label: REQUISITION_TYPE_LABEL[t],
                }))}
              />
              <Select<number | undefined>
                allowClear
                showSearch
                optionFilterProp="label"
                placeholder="Department"
                style={{ width: 'clamp(130px, 45vw, 190px)' }}
                value={search.departmentId}
                onChange={(v) => updateFilter({ departmentId: v })}
                loading={departmentsQuery.isLoading}
                options={(departmentsQuery.data ?? [])
                  .filter((d) => d.active !== false)
                  .map((d) => ({ value: d.id!, label: d.name }))}
              />
            </Space>
          </Space>
        }
      >
        <RequestState
          loading={listQuery.isLoading}
          variant="table"
          error={listQuery.isError ? listQuery.error : undefined}
          errorTitle="Couldn't load requisitions"
          onRetry={() => listQuery.refetch()}
          empty={!listQuery.isLoading && rows.length === 0}
          filterActive={hasActiveFilters}
          onClearFilters={clearAll}
          emptyFallback={
            <EmptyState
              icon={ClipboardList}
              title="No requisitions"
              description="Nothing matches the selected stage and filters right now."
            />
          }
        >
          <RequisitionTable
            data={rows}
            loading={listQuery.isFetching}
            departments={departmentsQuery.data}
            sections={sectionsQuery.data}
            designations={designationsQuery.data}
            grades={gradesQuery.data}
            onEdit={noopEdit}
            onOpen={setViewingDetail}
            // Same columns as the Approvals Inbox's Requisitions tab (plus
            // Status, since this page lists every status). The Details
            // drawer below stays editable — no `approvalView`.
            inboxLayout
            pagination={{
              current: page,
              pageSize: size,
              total: totalElements,
              showSizeChanger: true,
              pageSizeOptions: ['10', '20', '50', '100'],
              showTotal: (n, [from, to]) =>
                `${from}-${to} of ${n.toLocaleString('en-PK')}`,
            }}
            onChange={(pagination) =>
              navigate({
                search: (prev) => ({
                  ...prev,
                  page: pagination.current,
                  size: pagination.pageSize,
                }),
                replace: true,
              })
            }
          />
        </RequestState>
      </Section>

      <RequisitionHiresModal
        row={viewingHires}
        onClose={() => setViewingHires(null)}
        departments={departmentsQuery.data}
        sections={sectionsQuery.data}
        designations={designationsQuery.data}
        grades={gradesQuery.data}
      />

      <RequisitionDetailDrawer
        row={viewingDetail}
        onClose={() => setViewingDetail(null)}
        onEdit={noopEdit}
        onViewHires={setViewingHires}
        departments={departmentsQuery.data}
        sections={sectionsQuery.data}
        designations={designationsQuery.data}
        grades={gradesQuery.data}
        hideCancel
      />
    </div>
  );
}
