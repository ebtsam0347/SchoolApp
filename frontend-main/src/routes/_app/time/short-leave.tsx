// /time/short-leave — my short leaves, plus my team's and my department's
// for a manager.
//
// Sub-day leave: leave work between (startTime, endTime) on a given date
// with HR approval. Distinct from regularization (forgot-to-punch) and
// from full/half-day leave requests (/time/leaves).
//
// Team section: managers only (someone reports to them). Backed by
// `GET /short-leaves/team`, filtered by leave date and direct report. Names
// come from the row's `employee` summary if present, else from
// /employees/me/team.
//
// Department section: also managers only. Backed by
// `GET /short-leaves/department` — the same list the Generated team requests
// page shows — filtered by filed date and colleague (from the department
// roster).
//
// Against an attendance-service build without either route (or one where a
// manager can't read the department list yet) the section stays hidden
// rather than erroring.

import { useMemo, useState } from 'react';
import { createFileRoute } from '@tanstack/react-router';
import { keepPreviousData, useQuery } from '@tanstack/react-query';
import { Alert, Button } from 'antd';
import { Plus, Timer } from 'lucide-react';

import { PageHeader, RequestState, Section } from '@/components';
import { hasCode } from '@/lib';
import {
  attendanceApi,
  shortLeavesApi,
  type ShortLeaveStatus,
  type TeamShortLeaveRow,
} from '@/attendance';
import { useDepartmentRoster } from '@/people/department-roster';
import { ApplyShortLeaveDrawer, ShortLeaveTable } from '@/attendance/short-leaves';
import {
  RequestFilterBar,
  isFiltered,
  isSectionUnavailable,
  sectionPagination,
  toEmployeeOptions,
  useMyTeam,
  type RequestListFilters,
} from '@/attendance/request-filters';

export const Route = createFileRoute('/_app/time/short-leave')({
  component: ShortLeavePage,
});

const STATUS_OPTIONS: { value: ShortLeaveStatus; label: string }[] = [
  { value: 'PENDING', label: 'Pending' },
  { value: 'APPROVED', label: 'Approved' },
  { value: 'REJECTED', label: 'Rejected' },
  { value: 'CANCELLED', label: 'Cancelled' },
];

type Filters = RequestListFilters<ShortLeaveStatus>;

function ShortLeavePage() {
  const [filters, setFilters] = useState<Filters>({});
  const [page, setPage] = useState(0);
  const [pageSize, setPageSize] = useState(20);
  const [openApply, setOpenApply] = useState(false);

  const mineQuery = useQuery({
    queryKey: ['short-leaves', 'me', 'list', filters, page, pageSize],
    queryFn: ({ signal }) =>
      shortLeavesApi.mine(
        {
          status: filters.status,
          from: filters.from,
          to: filters.to,
          page,
          size: pageSize,
        },
        signal,
      ),
    placeholderData: keepPreviousData,
  });
  const filtered = isFiltered(filters);

  const pageData = mineQuery.data;
  const rows = pageData?.content ?? [];
  const totalElements = pageData?.totalElements ?? 0;

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
        title="My short leaves"
        description="Sub-day leave — step out between a start and end time on a given date, with HR approval."
        actions={
          <Button type="primary" icon={<Plus size={14} />} onClick={() => setOpenApply(true)}>
            Apply short leave
          </Button>
        }
      />

      <Section
        title="Short leave requests"
        subtitle={`${totalElements} request${totalElements === 1 ? '' : 's'}`}
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
          error={mineQuery.isError ? mineQuery.error : undefined}
          empty={!mineQuery.isLoading && totalElements === 0}
          emptyIcon={Timer}
          emptyTitle={filtered ? 'No matching short leaves' : 'No short leaves yet'}
          emptyDescription={
            filtered
              ? 'Try another date range or status, or clear the filters.'
              : "When you apply for a short leave, it'll show up here with the approval timeline."
          }
          emptyAction={
            !filtered ? (
              <Button
                type="primary"
                icon={<Plus size={14} />}
                onClick={() => setOpenApply(true)}
              >
                Apply your first short leave
              </Button>
            ) : undefined
          }
          errorTitle={
            hasCode(mineQuery.error, 'EMPLOYEE_NOT_FOUND')
              ? 'Your account isn\'t linked to an employee record'
              : "Couldn't load your short leave history"
          }
          errorFallback={
            hasCode(mineQuery.error, 'EMPLOYEE_NOT_FOUND')
              ? () => (
                  <Alert
                    type="info"
                    showIcon
                    title="Your account isn't linked to an employee record"
                    description="Short leaves are tracked per employee. Ask HR to link your login to your employee profile, then refresh."
                  />
                )
              : undefined
          }
        >
          <ShortLeaveTable
            data={rows}
            loading={mineQuery.isFetching}
            cancellable
            pagination={sectionPagination(page, pageSize, totalElements, setPage, setPageSize)}
          />
        </RequestState>
      </Section>

      <TeamShortLeaves />

      <DepartmentShortLeaves />

      <ApplyShortLeaveDrawer open={openApply} onClose={() => setOpenApply(false)} />
    </div>
  );
}

/**
 * Short leaves of the caller's direct reports — including ones the manager
 * filed on their behalf, which `/short-leaves/me` (the caller's own) never
 * lists. Renders nothing for someone with no reports, or while the endpoint
 * is missing.
 */
function TeamShortLeaves() {
  const [filters, setFilters] = useState<Filters>({});
  const [page, setPage] = useState(0);
  const [pageSize, setPageSize] = useState(20);

  const teamQuery = useMyTeam();
  const isManager = (teamQuery.data?.length ?? 0) > 0;
  const reportById = useMemo(
    () => new Map((teamQuery.data ?? []).map((e) => [e.id, e])),
    [teamQuery.data],
  );
  const employeeOptions = useMemo(() => toEmployeeOptions(teamQuery.data ?? []), [teamQuery.data]);

  const listQuery = useQuery({
    queryKey: ['short-leaves', 'team', 'list', filters, page, pageSize],
    queryFn: ({ signal }) =>
      shortLeavesApi.team(
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

  if (!isManager || isSectionUnavailable(listQuery.error)) return null;

  const rows = listQuery.data?.content ?? [];
  const totalElements = listQuery.data?.totalElements ?? 0;
  const filtered = isFiltered(filters);

  const employeeOf = (row: TeamShortLeaveRow) => {
    const e = row.employee ?? reportById.get(row.employeeId);
    return {
      fullName: e?.fullName ?? `#${row.employeeId}`,
      employeeCode: e?.employeeCode ?? null,
      profilePictureUrl: e?.profilePictureUrl ?? null,
    };
  };

  return (
    <Section
      title="Team short leaves"
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
        errorTitle="Couldn't load your team's short leaves"
        empty={!listQuery.isLoading && totalElements === 0}
        emptyIcon={Timer}
        emptyTitle={filtered ? 'No matching short leaves' : 'No team short leaves yet'}
        emptyDescription={
          filtered
            ? 'Try another employee, date range or status, or clear the filters.'
            : 'Short leaves your direct reports apply for — or you file for them — show up here.'
        }
      >
        <ShortLeaveTable<TeamShortLeaveRow>
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
 * Every short leave in the manager's department, whoever filed it — the
 * same list as the Generated team requests page's Short leave tab. Rows
 * carry the target's employee summary. Renders nothing for someone with no
 * reports, or while the endpoint is missing / closed to managers.
 */
function DepartmentShortLeaves() {
  const [filters, setFilters] = useState<Filters>({});
  const [page, setPage] = useState(0);
  const [pageSize, setPageSize] = useState(20);

  const teamQuery = useMyTeam();
  const isManager = (teamQuery.data?.length ?? 0) > 0;

  const listQuery = useQuery({
    queryKey: ['short-leaves', 'department', 'list', filters, page, pageSize],
    queryFn: ({ signal }) =>
      attendanceApi.shortLeavesDepartment(
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

  const rows = useMemo<TeamShortLeaveRow[]>(
    () =>
      (listQuery.data?.content ?? []).map(({ request, employee }) => ({
        ...request,
        employee,
      })),
    [listQuery.data],
  );

  if (hidden) return null;

  const totalElements = listQuery.data?.totalElements ?? 0;
  const filtered = isFiltered(filters);

  const employeeOf = (row: TeamShortLeaveRow) => ({
    fullName: row.employee?.fullName ?? `#${row.employeeId}`,
    employeeCode: row.employee?.employeeCode ?? null,
    profilePictureUrl: row.employee?.profilePictureUrl ?? null,
  });

  return (
    <Section
      title="Department short leaves"
      subtitle={`Every short leave in your department, whoever filed it · ${totalElements} request${totalElements === 1 ? '' : 's'}`}
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
        errorTitle="Couldn't load your department's short leaves"
        empty={!listQuery.isLoading && totalElements === 0}
        emptyIcon={Timer}
        emptyTitle={filtered ? 'No matching short leaves' : 'No department short leaves yet'}
        emptyDescription={
          filtered
            ? 'Try another employee, date range or status, or clear the filters.'
            : 'Short leaves anyone in your department applies for — or has filed for them — show up here.'
        }
      >
        <ShortLeaveTable<TeamShortLeaveRow>
          data={rows}
          loading={listQuery.isFetching}
          employeeOf={employeeOf}
          pagination={sectionPagination(page, pageSize, totalElements, setPage, setPageSize)}
        />
      </RequestState>
    </Section>
  );
}
