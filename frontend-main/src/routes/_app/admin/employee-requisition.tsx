// /admin/employee-requisition — headcount requests (new hires + replacements).
//
// Flow: DRAFT -> PENDING_APPROVAL -> APPROVED -> FULFILLED, with REJECTED /
// CANCELLED as terminal off-ramps.
//
// Manager surface. Two gates, both keyed off the MANAGER role, and they must
// agree:
//   1. The sidebar row (`requiresRole` in nav-config.ts).
//   2. `beforeLoad` below, so typing the URL isn't a way around the sidebar.
// Both call the same `hasAnyRole` from @/auth. Neither has an admin bypass —
// an ADMIN / HR account without MANAGER does not get this page.
//
// It used to be open to any linked employee, which meant a plain EMPLOYEE
// (including one holding only TEAM_REQUEST_GENERATION) could raise headcount
// requests. Raising one is a manager's call, so the role is now the gate.
//
// Approving a requisition is not here — those land in /inbox. The backend
// remains the authority for visible rows and actions, including approval
// decisions (see RequisitionRowActions).

import { useContext, useState } from 'react';
import { useQuery } from '@tanstack/react-query';
import {
  createFileRoute,
  redirect,
  useNavigate,
  useSearch,
} from '@tanstack/react-router';
import { Button, Segmented, Select, Space, Tooltip } from 'antd';
import {
  Ban,
  ClipboardList,
  FileCheck2,
  Lock,
  Plus,
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
import { AbilityContext, hasAnyRole, useAuth } from '@/auth';
import { readSnapshot } from '@/lib';
import { catalogsApi, peopleBff } from '@/people';
import {
  REQUISITION_STATUS_LABEL,
  REQUISITION_TYPE_LABEL,
  RequisitionDetailDrawer,
  RequisitionFormDrawer,
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
// Draft and Fulfilled are deliberately not segmented tabs — Draft is a
// requester's own unsubmitted scratch state, and Fulfilled is closed-out
// history; neither belongs alongside the active pipeline stages here.
// (STATUS_VALUES still includes both — used for `isStatus` validation and
// the row-actions edit/submit/mark-fulfilled gates.)
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

/** Role that owns this page. Mirrors nav-config's `requiresRole`. */
const REQUISITION_ROLE = 'MANAGER';

export const Route = createFileRoute('/_app/admin/employee-requisition')({
  // Gate (2) — see the header note. `_app` has already bounced guests, so a
  // missing snapshot here means the session is mid-bootstrap: let the page
  // render and leave the redirect to `_app`.
  beforeLoad: () => {
    const snap = readSnapshot();
    if (!snap?.user) return;
    if (!hasAnyRole([REQUISITION_ROLE], snap.user.roles)) {
      throw redirect({ to: '/' });
    }
  },
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
  const navigate = useNavigate({ from: '/admin/employee-requisition' });
  const search = useSearch({ from: '/_app/admin/employee-requisition' });
  const [drawerOpen, setDrawerOpen] = useState(false);
  const [editing, setEditing] = useState<RequisitionDto | null>(null);
  const [viewingHires, setViewingHires] = useState<RequisitionDto | null>(null);
  const [viewingDetail, setViewingDetail] = useState<RequisitionDto | null>(null);

  const page = search.page ?? 1;
  const size = search.size ?? 20;

  // Employee self-service gate — must match the Sidebar's check exactly
  // (`auth.user.employeeLinked`, see Sidebar/index.tsx). The sidebar decides
  // whether to show this row on that basis, so deriving access from anything
  // else lets the two disagree and strands the user on a denial screen after
  // a row the app told them to click.
  //
  // This previously read `!!profileQuery.data?.employee` off
  // /bff/account/profile with `retry: false`. That made access depend on a
  // network call whose `employee` field is optional, and treated every
  // failure mode — 403, timeout, employee-less payload — as "no access".
  // The backend stays the authority for which rows and actions they get.
  const auth = useAuth();
  const hasAccess = auth.user?.employeeLinked === true;

  // Department scoping. HR/admin (`write employee` — ADMIN inherits it via
  // `manage all`) see every department and keep the Department filter.
  // Everyone else — a line manager raising headcount for their own area — is
  // pinned to their own department: a requisition names a vacancy, a grade and
  // a reason for one team, and isn't theirs to read across the company.
  const ability = useContext(AbilityContext);
  const isHr = ability.can('write', 'employee');
  const scoped = hasAccess && !isHr;

  // Same cache entry and options as the Sidebar / persona callers, so the
  // scoped department costs no extra round-trip. Only the department id is
  // read here — access is decided above and never depends on this resolving.
  const profileQuery = useQuery({
    queryKey: ['account', 'profile'],
    queryFn: ({ signal }) => peopleBff.accountProfile(signal),
    staleTime: 60_000,
    enabled: scoped,
    retry: false,
  });
  const myDepartmentId = profileQuery.data?.employee?.departmentId;

  // Fail closed: until a scoped caller's department is known, ask for nothing.
  // Firing the list without a departmentId would return every department's
  // rows for the beat before the profile lands.
  const departmentId = scoped ? myDepartmentId : search.departmentId;
  const scopeReady = !scoped || typeof myDepartmentId === 'number';
  const canQuery = hasAccess && scopeReady;

  const listQuery = useQuery({
    queryKey: ['requisitions', search, departmentId],
    queryFn: ({ signal }) =>
      requisitionApi.list(
        {
          status: search.status,
          type: search.type,
          departmentId,
          page: page - 1,
          size,
        },
        signal,
      ),
    enabled: canQuery,
  });

  // The KPI counts carry the same departmentId as the list — otherwise a
  // scoped manager reads company-wide tiles above their own department's rows.
  // `departmentId` is part of each key too: without it React Query would hand
  // one department's count straight to the next (these keys are shared with
  // /people/queues/hr-requisition-approval, which runs unscoped).
  const draftCount = useQuery({
    queryKey: ['requisitions', 'count', 'DRAFT', departmentId],
    queryFn: ({ signal }) =>
      requisitionApi.list({ status: 'DRAFT', size: 1, departmentId }, signal),
    staleTime: 30_000,
    select: (d) => d.totalElements,
    enabled: canQuery,
  });
  const pendingCount = useQuery({
    queryKey: ['requisitions', 'count', 'PENDING_APPROVAL', departmentId],
    queryFn: ({ signal }) =>
      requisitionApi.list(
        { status: 'PENDING_APPROVAL', size: 1, departmentId },
        signal,
      ),
    staleTime: 30_000,
    select: (d) => d.totalElements,
    enabled: canQuery,
  });
  const approvedCount = useQuery({
    queryKey: ['requisitions', 'count', 'APPROVED', departmentId],
    queryFn: ({ signal }) =>
      requisitionApi.list({ status: 'APPROVED', size: 1, departmentId }, signal),
    staleTime: 30_000,
    select: (d) => d.totalElements,
    enabled: canQuery,
  });
  const rejectedCount = useQuery({
    queryKey: ['requisitions', 'count', 'REJECTED', departmentId],
    queryFn: ({ signal }) =>
      requisitionApi.list({ status: 'REJECTED', size: 1, departmentId }, signal),
    staleTime: 30_000,
    select: (d) => d.totalElements,
    enabled: canQuery,
  });
  const fulfilledCount = useQuery({
    queryKey: ['requisitions', 'count', 'FULFILLED', departmentId],
    queryFn: ({ signal }) =>
      requisitionApi.list({ status: 'FULFILLED', size: 1, departmentId }, signal),
    staleTime: 30_000,
    select: (d) => d.totalElements,
    enabled: canQuery,
  });
  const cancelledCount = useQuery({
    queryKey: ['requisitions', 'count', 'CANCELLED', departmentId],
    queryFn: ({ signal }) =>
      requisitionApi.list({ status: 'CANCELLED', size: 1, departmentId }, signal),
    staleTime: 30_000,
    select: (d) => d.totalElements,
    enabled: canQuery,
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

  // Label for the pinned department, so a scoped caller sees which one they're
  // looking at rather than inferring it from the rows.
  const myDepartmentName = (departmentsQuery.data ?? []).find(
    (d) => d.id === myDepartmentId,
  )?.name;

  // Draft (own unsubmitted scratch state) and Fulfilled (closed-out
  // history) shouldn't clutter the shared "All" queue admins/managers land
  // on by default — see HIDDEN_STATUSES above. The backend's list filter
  // only takes a single `status` (no exclusion), so this is a client-side
  // filter; a direct `?status=DRAFT`/`?status=FULFILLED` link still works
  // since there's no UI reason to hard-block reading a specific record.
  const rows = (listQuery.data?.content ?? []).filter(
    (r) => r.status === search.status || !HIDDEN_STATUSES.includes(r.status),
  );
  const hiddenCount = (draftCount.data ?? 0) + (fulfilledCount.data ?? 0);
  const totalElements = search.status
    ? (listQuery.data?.totalElements ?? 0)
    : Math.max(0, (listQuery.data?.totalElements ?? 0) - hiddenCount);

  // The pinned department isn't a filter the user chose, so it must not make
  // "Clear filters" look active for a scoped caller.
  const hasActiveFilters = Boolean(
    search.status || search.type || (!scoped && search.departmentId),
  );

  const updateFilter = (patch: Partial<Search>) =>
    navigate({ search: (prev) => ({ ...prev, ...patch, page: 1 }), replace: true });

  const clearAll = () => navigate({ search: () => ({}), replace: true });

  const openCreate = () => {
    setEditing(null);
    setDrawerOpen(true);
  };
  const openEdit = (row: RequisitionDto) => {
    setEditing(row);
    setDrawerOpen(true);
  };

  // No async resolve step any more: `_app` already blocks rendering until
  // auth settles, so `auth.user` is populated by the time this route paints.
  if (!hasAccess) {
    return (
      <div style={{ padding: '64px 24px', maxWidth: 480, margin: '0 auto' }}>
        <EmptyState
          icon={Lock}
          title="You don't have access to this page"
          description="Employee requisitions are available to employees with a linked employee record."
        />
      </div>
    );
  }

  // Scoped caller whose employee record carries no department. Failing closed
  // here is deliberate — falling back to an unscoped list would quietly show
  // them every department's requisitions, which is the thing this gate exists
  // to prevent.
  if (scoped && profileQuery.isFetched && typeof myDepartmentId !== 'number') {
    return (
      <div style={{ padding: '64px 24px', maxWidth: 480, margin: '0 auto' }}>
        <EmptyState
          icon={Lock}
          title="We couldn't determine your department"
          description="Hiring requisitions are listed per department, and your employee record doesn't have one set. Ask HR to set your department, then reload this page."
        />
      </div>
    );
  }

  return (
    <div style={{ display: 'flex', flexDirection: 'column', gap: 24, width: '100%' }}>
      <PageHeader
        eyebrow="Time"
        title="Hiring Requisitions"
        description="Manage requests for new hires and replacements. Flow: Pending approval → Approved."
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
            {hasAccess ? (
              <Button type="primary" icon={<Plus size={14} />} onClick={openCreate}>
                New requisition
              </Button>
            ) : null}
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
        subtitle={
          scoped
            ? `${totalElements.toLocaleString('en-PK')} total in ${myDepartmentName ?? 'your department'}`
            : `${totalElements.toLocaleString('en-PK')} total`
        }
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
                allowClear={!scoped}
                showSearch={!scoped}
                disabled={scoped}
                optionFilterProp="label"
                placeholder="Department"
                title={
                  scoped
                    ? 'Requisitions are shown for your own department'
                    : undefined
                }
                style={{ width: 'clamp(130px, 45vw, 190px)' }}
                value={departmentId}
                onChange={(v) => updateFilter({ departmentId: v })}
                loading={departmentsQuery.isLoading || profileQuery.isLoading}
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
              title="No requisitions yet"
              description="Raise a requisition to request a new hire or a replacement — HR will review and approve it."
              action={
                hasAccess ? (
                  <Button type="primary" icon={<Plus size={14} />} onClick={openCreate}>
                    New requisition
                  </Button>
                ) : null
              }
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
            onEdit={openEdit}
            onOpen={setViewingDetail}
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

      <RequisitionFormDrawer
        open={drawerOpen}
        onClose={() => setDrawerOpen(false)}
        requisition={editing}
      />

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
        onEdit={openEdit}
        onViewHires={setViewingHires}
        departments={departmentsQuery.data}
        sections={sectionsQuery.data}
        designations={designationsQuery.data}
        grades={gradesQuery.data}
      />
    </div>
  );
}
