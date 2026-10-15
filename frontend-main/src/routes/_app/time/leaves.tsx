// /time/leaves — my leaves, plus my team's and my department's for a
// manager.
//
// Top: leave-balances strip (one card per leave type) and an apply-leave
// CTA drawer. Below: my leave requests, filtered by leave date (defaults to
// the selected year) and status. Click a row → drawer with the per-day
// breakdown and (when eligible) cancel button.
//
// Team section: managers only (someone reports to them). Backed by
// `GET /leaves/team`, filtered by leave date, direct report and status.
//
// Department section: also managers only. Backed by `GET /leaves/department`
// — the same list the Generated team requests page shows — filtered by filed
// date, colleague (from the department roster) and status.
//
// Against an attendance-service build without either route (or one where a
// manager can't read the department list yet) the section stays hidden
// rather than erroring.
//
// Subcomponents — BalanceCard, LeaveTable, ApplyLeaveDrawer,
// LeaveDetailDrawer, ApprovalStepsTimeline — live in attendance/leaves/.

import { useMemo, useState } from 'react';
import { createFileRoute, useNavigate, useSearch } from '@tanstack/react-router';
import { keepPreviousData, useQuery } from '@tanstack/react-query';
import { Alert, Button, Select, Space } from 'antd';
import { Plane, Plus } from 'lucide-react';

import {
  KpiGrid,
  PageHeader,
  RequestState,
  Section,
  StatCard,
} from '@/components';
import { hasCode } from '@/lib';
import {
  attendanceApi,
  leavesApi,
  type LeaveRequestDto,
  type LeaveStatus,
  type TeamLeaveRow,
} from '@/attendance';
import { peopleBff } from '@/people';
import { useDepartmentRoster } from '@/people/department-roster';
import {
  ApplyLeaveDrawer,
  BalanceCard,
  LeaveDetailDrawer,
  LeaveTable,
} from '@/attendance/leaves';
import {
  RequestFilterBar,
  isFiltered,
  isSectionUnavailable,
  sectionPagination,
  toEmployeeOptions,
  useMyTeam,
  type RequestListFilters,
} from '@/attendance/request-filters';

type LeavesSearch = {
  detail?: number;
};

export const Route = createFileRoute('/_app/time/leaves')({
  validateSearch: (s): LeavesSearch => ({
    detail:
      typeof s.detail === 'number'
        ? s.detail
        : typeof s.detail === 'string' && s.detail.length > 0
          ? Number(s.detail) || undefined
          : undefined,
  }),
  component: LeavesPage,
});

const STATUS_OPTIONS: { value: LeaveStatus; label: string }[] = [
  { value: 'PENDING', label: 'Pending' },
  { value: 'APPROVED', label: 'Approved' },
  { value: 'PARTIALLY_APPROVED', label: 'Partial' },
  { value: 'REJECTED', label: 'Rejected' },
  { value: 'CANCELLED', label: 'Cancelled' },
];

type Filters = RequestListFilters<LeaveStatus>;

function yearOptions() {
  const cur = new Date().getFullYear();
  return [cur + 1, cur, cur - 1, cur - 2].map((y) => ({
    value: y,
    label: String(y),
  }));
}

/** Who a team / department row is for. */
const employeeOf = (row: TeamLeaveRow) => ({
  fullName: row.employee?.fullName ?? `#${row.employeeId}`,
  employeeCode: row.employee?.employeeCode ?? null,
  profilePictureUrl: row.employee?.profilePictureUrl ?? null,
});

function LeavesPage() {
  const search = useSearch({ from: '/_app/time/leaves' });
  const navigate = useNavigate({ from: '/time/leaves' });
  const [filters, setFilters] = useState<Filters>({});
  const [year, setYear] = useState(() => new Date().getFullYear());
  const [page, setPage] = useState(0);
  const [pageSize, setPageSize] = useState(20);
  const [openApply, setOpenApply] = useState(false);
  const detailId = search.detail ?? null;
  // The row the drawer was opened from, so it needn't re-fetch the request.
  const [detailRow, setDetailRow] = useState<LeaveRequestDto | null>(null);
  const setDetailId = (next: number | null) => {
    void navigate({
      search: (s) => ({ ...s, detail: next ?? undefined }),
      replace: true,
    });
  };

  // An explicit date range wins; otherwise the selected year.
  const hasRange = !!(filters.from || filters.to);
  const from = hasRange ? filters.from : `${year}-01-01`;
  const to = hasRange ? filters.to : `${year}-12-31`;

  // No year → the active cycle (correct for Jul–Jun fiscal types too); a past
  // year → that calendar year's cycles, for a historical view.
  const balancesQuery = useQuery({
    queryKey: ['leaves', 'me', 'balances', year],
    queryFn: ({ signal }) =>
      leavesApi.myBalances(
        year === new Date().getFullYear() ? undefined : year,
        signal,
      ),
    staleTime: 60_000,
  });
  const myLeavesQuery = useQuery({
    queryKey: ['leaves', 'me', 'list', from, to, filters.status, page, pageSize],
    queryFn: ({ signal }) =>
      leavesApi.myLeaves(
        { status: filters.status, from, to, page, size: pageSize },
        signal,
      ),
    placeholderData: keepPreviousData,
  });

  const profileQuery = useQuery({
    queryKey: ['bff', 'account', 'profile'],
    queryFn: ({ signal }) => peopleBff.accountProfile(signal),
    staleTime: 60_000,
  });

  const balances = balancesQuery.data ?? [];
  const pageData = myLeavesQuery.data;
  const rows = pageData?.content ?? [];
  const totalElements = pageData?.totalElements ?? 0;
  const me = profileQuery.data?.employee;
  const filtered = isFiltered(filters);

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
        eyebrow="Time"
        title="My leaves"
        description="Annual, sick, casual — apply, track approvals, see your balance."
        actions={
          <Space>
            <Select
              size="middle"
              value={year}
              onChange={(y) => {
                setYear(y);
                setPage(0);
              }}
              options={yearOptions()}
              style={{ width: 100 }}
            />
            <Button type="primary" icon={<Plus size={14} />} onClick={() => setOpenApply(true)}>
              Apply leave
            </Button>
          </Space>
        }
      />

      <KpiGrid minColumnWidth={220}>
        {balances.length === 0 ? (
          <StatCard
            label="Balances"
            value={balancesQuery.isLoading ? 'Loading…' : 'Not configured'}
            icon={Plane}
            variant="plain"
          />
        ) : (
          balances.map((bal, i) => (
            <BalanceCard key={bal.id} bal={bal} highlight={i === 0} />
          ))
        )}
      </KpiGrid>

      <Section
        title="Leave requests"
        subtitle={`${totalElements} request${totalElements === 1 ? '' : 's'}${
          hasRange ? '' : ` in ${year}`
        }`}
        actions={
          <RequestFilterBar
            value={filters}
            statusOptions={STATUS_OPTIONS}
            onChange={(next) => {
              setFilters(next);
              setPage(0);
            }}
            dateLabel="Leave"
            allowFuture
          />
        }
      >
        <RequestState
          loading={false}
          error={myLeavesQuery.isError ? myLeavesQuery.error : undefined}
          empty={!myLeavesQuery.isLoading && totalElements === 0}
          emptyIcon={Plane}
          emptyTitle={filtered ? 'No matching leaves' : 'No leaves yet'}
          emptyDescription={
            filtered
              ? 'Try another date range or status, or clear the filters.'
              : "When you apply for leave, it'll show up here with the approval timeline."
          }
          emptyAction={
            !filtered ? (
              <Button
                type="primary"
                icon={<Plus size={14} />}
                onClick={() => setOpenApply(true)}
              >
                Apply your first leave
              </Button>
            ) : undefined
          }
          errorTitle={
            hasCode(myLeavesQuery.error, 'EMPLOYEE_NOT_FOUND')
              ? 'Your account isn\'t linked to an employee record'
              : "Couldn't load your leave history"
          }
          errorFallback={
            hasCode(myLeavesQuery.error, 'EMPLOYEE_NOT_FOUND')
              ? () => (
                  <Alert
                    type="info"
                    showIcon
                    title="Your account isn't linked to an employee record"
                    description="Leaves are tracked per employee. Ask HR to link your login to your employee profile, then refresh."
                  />
                )
              : undefined
          }
        >
          <LeaveTable
            data={rows}
            loading={myLeavesQuery.isFetching}
            onOpen={(row) => {
              setDetailRow(row);
              setDetailId(row.id);
            }}
            employeeOf={
              me
                ? () => ({
                    fullName: me.fullName ?? '—',
                    employeeCode: me.employeeCode ?? null,
                    profilePictureUrl: me.profilePictureUrl ?? null,
                  })
                : undefined
            }
            pagination={sectionPagination(page, pageSize, totalElements, setPage, setPageSize)}
          />
        </RequestState>
      </Section>

      <TeamLeaves />

      <DepartmentLeaves />

      <ApplyLeaveDrawer open={openApply} onClose={() => setOpenApply(false)} />
      <LeaveDetailDrawer
        leaveId={detailId}
        initial={detailRow}
        onClose={() => setDetailId(null)}
      />
    </div>
  );
}

/**
 * Leaves of the caller's direct reports — including ones the manager filed
 * on their behalf, which `/leaves/me` (the caller's own) never lists.
 * Renders nothing for someone with no reports, or while the endpoint is
 * missing.
 */
function TeamLeaves() {
  const [filters, setFilters] = useState<Filters>({});
  const [page, setPage] = useState(0);
  const [pageSize, setPageSize] = useState(20);

  const teamQuery = useMyTeam();
  const isManager = (teamQuery.data?.length ?? 0) > 0;
  const employeeOptions = useMemo(() => toEmployeeOptions(teamQuery.data ?? []), [teamQuery.data]);

  const listQuery = useQuery({
    queryKey: ['leaves', 'team', 'list', filters, page, pageSize],
    queryFn: ({ signal }) =>
      leavesApi.team(
        {
          status: filters.status,
          employeeId: filters.employeeId,
          from: filters.from,
          to: filters.to,
          page,
          size: pageSize,
        },
        signal,
      ),
    enabled: isManager,
    placeholderData: keepPreviousData,
    retry: (count, err) => !isSectionUnavailable(err) && count < 2,
  });

  const rows = useMemo<TeamLeaveRow[]>(
    () => (listQuery.data?.content ?? []).map(({ request, employee }) => ({ ...request, employee })),
    [listQuery.data],
  );

  if (!isManager || isSectionUnavailable(listQuery.error)) return null;

  const totalElements = listQuery.data?.totalElements ?? 0;
  const filtered = isFiltered(filters);

  return (
    <Section
      title="Team leaves"
      subtitle={`Your direct reports' requests — including ones you filed for them · ${totalElements} request${totalElements === 1 ? '' : 's'}`}
      actions={
        <RequestFilterBar
          value={filters}
          statusOptions={STATUS_OPTIONS}
          onChange={(next) => {
            setFilters(next);
            setPage(0);
          }}
          employeeOptions={employeeOptions}
          employeesLoading={teamQuery.isLoading}
          dateLabel="Leave"
          allowFuture
        />
      }
    >
      <RequestState
        loading={false}
        error={listQuery.isError ? listQuery.error : undefined}
        errorTitle="Couldn't load your team's leaves"
        empty={!listQuery.isLoading && totalElements === 0}
        emptyIcon={Plane}
        emptyTitle={filtered ? 'No matching leaves' : 'No team leaves yet'}
        emptyDescription={
          filtered
            ? 'Try another employee, date range or status, or clear the filters.'
            : 'Leaves your direct reports apply for — or you file for them — show up here.'
        }
      >
        <LeaveTable<TeamLeaveRow>
          data={rows}
          loading={listQuery.isFetching}
          employeeOf={employeeOf}
          pagination={sectionPagination(page, pageSize, totalElements, setPage, setPageSize)}
        />
      </RequestState>
    </Section>
  );
}

/**
 * Every leave in the manager's department, whoever filed it — the same list
 * as the Generated team requests page's Leave tab. Renders nothing for
 * someone with no reports, or while the endpoint is missing / closed to
 * managers.
 */
function DepartmentLeaves() {
  const [filters, setFilters] = useState<Filters>({});
  const [page, setPage] = useState(0);
  const [pageSize, setPageSize] = useState(20);

  const teamQuery = useMyTeam();
  const isManager = (teamQuery.data?.length ?? 0) > 0;

  const listQuery = useQuery({
    queryKey: ['leaves', 'department', 'list', filters, page, pageSize],
    queryFn: ({ signal }) =>
      attendanceApi.leavesDepartment(
        {
          status: filters.status,
          employeeId: filters.employeeId,
          filedFrom: filters.from,
          filedTo: filters.to,
          page,
          size: pageSize,
        },
        signal,
      ),
    enabled: isManager,
    placeholderData: keepPreviousData,
    retry: (count, err) => !isSectionUnavailable(err) && count < 2,
  });
  const hidden = !isManager || isSectionUnavailable(listQuery.error);

  const roster = useDepartmentRoster({ enabled: !hidden });
  const employeeOptions = useMemo(() => toEmployeeOptions(roster.rows), [roster.rows]);

  const rows = useMemo<TeamLeaveRow[]>(
    () => (listQuery.data?.content ?? []).map(({ request, employee }) => ({ ...request, employee })),
    [listQuery.data],
  );

  if (hidden) return null;

  const totalElements = listQuery.data?.totalElements ?? 0;
  const filtered = isFiltered(filters);

  return (
    <Section
      title="Department leaves"
      subtitle={`Every leave in your department, whoever filed it · ${totalElements} request${totalElements === 1 ? '' : 's'}`}
      actions={
        <RequestFilterBar
          value={filters}
          statusOptions={STATUS_OPTIONS}
          onChange={(next) => {
            setFilters(next);
            setPage(0);
          }}
          employeeOptions={employeeOptions}
          employeesLoading={roster.isLoading}
          dateLabel="Filed"
        />
      }
    >
      <RequestState
        loading={false}
        error={listQuery.isError ? listQuery.error : undefined}
        errorTitle="Couldn't load your department's leaves"
        empty={!listQuery.isLoading && totalElements === 0}
        emptyIcon={Plane}
        emptyTitle={filtered ? 'No matching leaves' : 'No department leaves yet'}
        emptyDescription={
          filtered
            ? 'Try another employee, date range or status, or clear the filters.'
            : 'Leaves anyone in your department applies for — or has filed for them — show up here.'
        }
      >
        <LeaveTable<TeamLeaveRow>
          data={rows}
          loading={listQuery.isFetching}
          employeeOf={employeeOf}
          pagination={sectionPagination(page, pageSize, totalElements, setPage, setPageSize)}
        />
      </RequestState>
    </Section>
  );
}
