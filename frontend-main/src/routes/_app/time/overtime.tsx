// /time/overtime — my OT requests, plus my team's and my department's for a
// manager, and bulk apply.
//
// Authorization-only flow: submit dates worked beyond shift, a manager
// approves, and minutes are derived from attendance punches once the final
// stage approves. Bulk apply is gated behind OVERTIME_APPLY_BULK and tags
// each created row with a shared batch id.
//
// My list comes from the BFF (`/overtime/me/with-records`, unpaged, with
// the punch-derived minutes), so its work-date / status filters and paging
// run client-side.
//
// Team section: managers only (someone reports to them). Backed by
// `GET /overtime/team`, filtered by work date, direct report and status.
//
// Department section: also managers only. Backed by
// `GET /overtime/department` — the same list the Generated team requests
// page shows — filtered by filed date, colleague (from the department
// roster) and status.
//
// Against an attendance-service build without either route (or one where a
// manager can't read the department list yet) the section stays hidden
// rather than erroring.

import { useMemo, useState } from 'react';
import { createFileRoute } from '@tanstack/react-router';
import { keepPreviousData, useQuery } from '@tanstack/react-query';
import { Button, Space } from 'antd';
import {
  AlertTriangle,
  Boxes,
  CheckCircle2,
  Plus,
  Sparkles,
  XCircle,
} from 'lucide-react';

import {
  attendanceApi,
  overtimeApi,
  overtimeBff,
  type OvertimeListRow,
  type OvertimeStatus,
} from '@/attendance';
import {
  ApplyOvertimeDrawer,
  BulkApplyOvertimeDrawer,
  OvertimeTable,
} from '@/attendance/overtime';
import {
  RequestFilterBar,
  isFiltered,
  isSectionUnavailable,
  sectionPagination,
  toEmployeeOptions,
  useMyTeam,
  type RequestListFilters,
} from '@/attendance/request-filters';
import { Can, useAuth } from '@/auth';
import {
  EmptyState,
  KpiGrid,
  PageHeader,
  RequestState,
  Section,
  StatCard,
} from '@/components';
import { useDepartmentRoster } from '@/people/department-roster';

export const Route = createFileRoute('/_app/time/overtime')({
  component: OvertimePage,
});

const STATUS_OPTIONS: { value: OvertimeStatus; label: string }[] = [
  { value: 'PENDING', label: 'Pending' },
  { value: 'APPROVED', label: 'Approved' },
  { value: 'REJECTED', label: 'Rejected' },
  { value: 'CANCELLED', label: 'Cancelled' },
];

type Filters = RequestListFilters<OvertimeStatus>;

/** Who a team / department row is for. */
const employeeOf = (row: OvertimeListRow) => ({
  fullName: row.employee?.fullName ?? `#${row.employeeId}`,
  employeeCode: row.employee?.employeeCode ?? null,
  profilePictureUrl: row.employee?.profilePictureUrl ?? null,
});

function OvertimePage() {
  const auth = useAuth();
  const [filters, setFilters] = useState<Filters>({});
  const [page, setPage] = useState(0);
  const [pageSize, setPageSize] = useState(20);
  const [openApply, setOpenApply] = useState(false);
  const [openBulk, setOpenBulk] = useState(false);

  const mineQuery = useQuery({
    queryKey: ['bff', 'overtime', 'me'],
    queryFn: ({ signal }) => overtimeBff.mine(signal),
    enabled: auth.user?.employeeLinked === true,
  });

  const all = useMemo<OvertimeListRow[]>(
    () =>
      (mineQuery.data ?? []).map(({ request, ...minutes }) => ({ ...request, minutes })),
    [mineQuery.data],
  );
  const filtered = all.filter(
    (r) =>
      (!filters.status || r.status === filters.status) &&
      (!filters.from || r.workDate >= filters.from) &&
      (!filters.to || r.workDate <= filters.to),
  );
  const rows = filtered.slice(page * pageSize, (page + 1) * pageSize);
  const filterActive = isFiltered(filters);

  const counts = {
    pending: all.filter((r) => r.status === 'PENDING').length,
    approved: all.filter((r) => r.status === 'APPROVED').length,
    rejected: all.filter((r) => r.status === 'REJECTED').length,
  };

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
        eyebrow="Time"
        title="Overtime"
        description="Authorization for the days you worked beyond your shift."
        actions={
          <Space>
            <Can I="bulk" a="attendance.overtime">
              <Button icon={<Boxes size={14} />} onClick={() => setOpenBulk(true)}>
                Bulk apply
              </Button>
            </Can>
            <Button
              type="primary"
              icon={<Plus size={14} />}
              onClick={() => setOpenApply(true)}
            >
              Request OT
            </Button>
          </Space>
        }
      />

      <KpiGrid minColumnWidth={200}>
        <StatCard
          variant="accent-radial"
          tone="warning"
          icon={Sparkles}
          accentCorner="tr"
          label="Pending"
          value={`${counts.pending}`}
          delta={
            counts.pending === 1
              ? 'request awaiting approval'
              : 'requests awaiting approval'
          }
        />
        <StatCard
          variant="plain"
          tone="success"
          icon={CheckCircle2}
          label="Approved"
          value={`${counts.approved}`}
          delta="counted toward this month's OT"
        />
        <StatCard
          variant="plain"
          tone={counts.rejected > 0 ? 'error' : 'neutral'}
          icon={XCircle}
          label="Rejected"
          value={`${counts.rejected}`}
        />
        <StatCard
          variant="plain"
          icon={AlertTriangle}
          tone="info"
          label="Submission rule"
          value="1 punch required"
          delta="On the work date — anyone without a punch is skipped"
        />
      </KpiGrid>

      <Section
        title="My OT requests"
        subtitle={`${filtered.length} request${filtered.length === 1 ? '' : 's'}`}
        actions={
          <RequestFilterBar
            value={filters}
            statusOptions={STATUS_OPTIONS}
            onChange={(next) => {
              setFilters(next);
              setPage(0);
            }}
            dateLabel="Work"
            allowFuture
          />
        }
      >
        <RequestState
          loading={false}
          error={mineQuery.isError ? mineQuery.error : undefined}
          errorTitle="Couldn't load OT requests"
          empty={!mineQuery.isLoading && filtered.length === 0}
          emptyFallback={
            filterActive ? (
              <EmptyState
                icon={Sparkles}
                title="No matching OT requests"
                description="Try another date range or status, or clear the filters."
              />
            ) : (
              <EmptyState
                icon={Sparkles}
                title="No OT yet"
                description="Pick a date you worked extra and request authorization. Your manager (and HR) will see it in their inbox."
                action={
                  <Button
                    type="primary"
                    icon={<Plus size={14} />}
                    onClick={() => setOpenApply(true)}
                  >
                    Request OT
                  </Button>
                }
              />
            )
          }
        >
          <OvertimeTable
            data={rows}
            loading={mineQuery.isFetching}
            showMinutes
            cancellable
            pagination={sectionPagination(page, pageSize, filtered.length, setPage, setPageSize)}
          />
        </RequestState>
      </Section>

      <TeamOvertime />

      <DepartmentOvertime />

      <ApplyOvertimeDrawer open={openApply} onClose={() => setOpenApply(false)} />
      <BulkApplyOvertimeDrawer open={openBulk} onClose={() => setOpenBulk(false)} />
    </div>
  );
}

/**
 * Overtime of the caller's direct reports — including requests the manager
 * filed on their behalf (single or bulk), which the caller's own list never
 * shows. Renders nothing for someone with no reports, or while the endpoint
 * is missing.
 */
function TeamOvertime() {
  const [filters, setFilters] = useState<Filters>({});
  const [page, setPage] = useState(0);
  const [pageSize, setPageSize] = useState(20);

  const teamQuery = useMyTeam();
  const isManager = (teamQuery.data?.length ?? 0) > 0;
  const employeeOptions = useMemo(() => toEmployeeOptions(teamQuery.data ?? []), [teamQuery.data]);

  const listQuery = useQuery({
    queryKey: ['overtime', 'team', 'list', filters, page, pageSize],
    queryFn: ({ signal }) =>
      overtimeApi.team(
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

  const rows = useMemo<OvertimeListRow[]>(
    () =>
      (listQuery.data?.content ?? []).map(({ request, employee, minutes }) => ({
        ...request,
        employee,
        minutes: minutes ?? undefined,
      })),
    [listQuery.data],
  );

  if (!isManager || isSectionUnavailable(listQuery.error)) return null;

  const totalElements = listQuery.data?.totalElements ?? 0;
  const filtered = isFiltered(filters);

  return (
    <Section
      title="Team OT requests"
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
          dateLabel="Work"
          allowFuture
        />
      }
    >
      <RequestState
        loading={false}
        error={listQuery.isError ? listQuery.error : undefined}
        errorTitle="Couldn't load your team's OT requests"
        empty={!listQuery.isLoading && totalElements === 0}
        emptyIcon={Sparkles}
        emptyTitle={filtered ? 'No matching OT requests' : 'No team OT requests yet'}
        emptyDescription={
          filtered
            ? 'Try another employee, date range or status, or clear the filters.'
            : 'Overtime your direct reports request — or you file for them — shows up here.'
        }
      >
        <OvertimeTable<OvertimeListRow>
          data={rows}
          loading={listQuery.isFetching}
          employeeOf={employeeOf}
          showMinutes
          pagination={sectionPagination(page, pageSize, totalElements, setPage, setPageSize)}
        />
      </RequestState>
    </Section>
  );
}

/**
 * Every overtime request in the manager's department, whoever filed it —
 * the same list as the Generated team requests page's Overtime tab. Renders
 * nothing for someone with no reports, or while the endpoint is missing /
 * closed to managers.
 */
function DepartmentOvertime() {
  const [filters, setFilters] = useState<Filters>({});
  const [page, setPage] = useState(0);
  const [pageSize, setPageSize] = useState(20);

  const teamQuery = useMyTeam();
  const isManager = (teamQuery.data?.length ?? 0) > 0;

  const listQuery = useQuery({
    queryKey: ['overtime', 'department', 'list', filters, page, pageSize],
    queryFn: ({ signal }) =>
      attendanceApi.overtimeDepartment(
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

  const rows = useMemo<OvertimeListRow[]>(
    () =>
      (listQuery.data?.content ?? []).map(({ request, employee, minutes }) => ({
        ...request,
        employee,
        minutes: minutes ?? undefined,
      })),
    [listQuery.data],
  );

  if (hidden) return null;

  const totalElements = listQuery.data?.totalElements ?? 0;
  const filtered = isFiltered(filters);

  return (
    <Section
      title="Department OT requests"
      subtitle={`Every overtime request in your department, whoever filed it · ${totalElements} request${totalElements === 1 ? '' : 's'}`}
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
        errorTitle="Couldn't load your department's OT requests"
        empty={!listQuery.isLoading && totalElements === 0}
        emptyIcon={Sparkles}
        emptyTitle={filtered ? 'No matching OT requests' : 'No department OT requests yet'}
        emptyDescription={
          filtered
            ? 'Try another employee, date range or status, or clear the filters.'
            : 'Overtime anyone in your department requests — or has filed for them — shows up here.'
        }
      >
        <OvertimeTable<OvertimeListRow>
          data={rows}
          loading={listQuery.isFetching}
          employeeOf={employeeOf}
          showMinutes
          pagination={sectionPagination(page, pageSize, totalElements, setPage, setPageSize)}
        />
      </RequestState>
    </Section>
  );
}
