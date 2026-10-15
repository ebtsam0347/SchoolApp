// /people/$id/employment — contract history + org-assignment history +
// direct reports table + the 5 change-* modals (manager / department /
// section / designation / grade).
//
// Subcomponents — AssignmentTile, ContractTable, HistoryTable,
// ChangeAssignmentModal — live in src/people/employment/.

import { useState } from 'react';
import { createFileRoute, useParams } from '@tanstack/react-router';
import { useQuery } from '@tanstack/react-query';
import {
  Button,
  Dropdown,
  Input,
  Skeleton,
  Space,
  Table,
  type MenuProps,
  type TableColumnsType,
} from 'antd';
import {
  ChevronDown,
  Network,
  Pencil,
  Search,
  Star,
  UserSquare,
  Users as UsersIcon,
} from 'lucide-react';

import {
  RequestState,
  Section,
  personColumn,
  statusColumn,
} from '@/components';
import { useDebounced } from '@/lib';
import {
  employeesApi,
  peopleBff,
  EMPLOYMENT_STATUS_LABEL,
  EMPLOYMENT_STATUS_TONE,
  type EmployeeDto,
  type EmploymentStatus,
} from '@/people';
import { WarningsPanel } from '@/people/disciplinary';
import {
  AssignmentTile,
  ChangeAssignmentModal,
  ContractTable,
  HistoryTable,
  type ChangeKind,
} from '@/people/employment';

export const Route = createFileRoute('/_app/people/$id/employment')({
  component: EmploymentTab,
});

const teamColumns: TableColumnsType<EmployeeDto> = [
  personColumn<EmployeeDto>({
    title: 'Employee',
    dataIndex: (row) =>
      row.fullName
        ? {
            fullName: row.fullName,
            employeeCode: row.employeeCode,
            profilePictureUrl: row.profilePictureUrl,
          }
        : null,
    to: (row) => (row.id ? `/people/${row.id}` : undefined),
    size: 'compact',
  }),
  statusColumn<EmployeeDto, EmploymentStatus>({
    title: 'Status',
    dataIndex: (row) => (row.employmentStatus ?? 'ACTIVE') as EmploymentStatus,
    toneMap: EMPLOYMENT_STATUS_TONE,
    labelMap: EMPLOYMENT_STATUS_LABEL,
    size: 'sm',
    width: 110,
  }),
  {
    title: 'Reports',
    key: 'directReportsCount',
    align: 'right',
    width: 90,
    render: (_, row) =>
      row.directReportsCount && row.directReportsCount > 0 ? (
        <span style={{ fontVariantNumeric: 'tabular-nums' }}>
          {row.directReportsCount}
        </span>
      ) : (
        <span style={{ opacity: 0.45 }}>—</span>
      ),
  },
];

function EmploymentTab() {
  const { id } = useParams({ from: '/_app/people/$id/employment' });
  const employeeId = Number(id);
  const [changeOpen, setChangeOpen] = useState<ChangeKind | null>(null);
  const [teamPage, setTeamPage] = useState(1);
  const [teamPageSize, setTeamPageSize] = useState(20);
  const [teamSearchInput, setTeamSearchInput] = useState('');
  const teamSearch = useDebounced(teamSearchInput.trim(), 250);

  const detailQuery = useQuery({
    queryKey: ['people', 'detail', employeeId],
    queryFn: ({ signal }) => peopleBff.detail(employeeId, signal),
    enabled: Number.isFinite(employeeId),
  });

  const contractsQuery = useQuery({
    queryKey: ['people', 'contracts', employeeId],
    queryFn: ({ signal }) => employeesApi.contracts(employeeId, signal),
    enabled: Number.isFinite(employeeId),
  });

  const historyQuery = useQuery({
    queryKey: ['people', 'bff-history', employeeId],
    // BFF endpoint resolves catalog labels + manager summary so the
    // table can render names instead of FK ids. The /employees/{id}/history
    // upstream is intentionally deprecated for FE use.
    queryFn: ({ signal }) =>
      peopleBff.history(employeeId, { size: 50 }, signal),
    enabled: Number.isFinite(employeeId),
  });

  const teamQuery = useQuery({
    queryKey: ['people', 'team', employeeId, teamPage, teamPageSize, teamSearch],
    queryFn: ({ signal }) =>
      employeesApi.team(
        employeeId,
        {
          page: teamPage - 1,
          size: teamPageSize,
          q: teamSearch || undefined,
        },
        signal,
      ),
    enabled: Number.isFinite(employeeId),
    placeholderData: (prev) => prev,
  });

  const detail = detailQuery.data;
  const e = detail?.employee;
  const r = detail?.resolved;

  return (
    <div
      style={{
        display: 'flex',
        flexDirection: 'column',
        gap: 'var(--app-space-lg)',
      }}
    >
      <Section
        title="Current assignment"
        subtitle="Department, section, designation, grade, and manager. Each change is recorded in the history below."
        actions={
          <Dropdown
            trigger={['click']}
            menu={
              {
                items: [
                  {
                    key: 'department',
                    label: 'Change department',
                    icon: <Network size={14} />,
                  },
                  {
                    key: 'section',
                    label: 'Change section',
                    icon: <Network size={14} />,
                  },
                  {
                    key: 'designation',
                    label: 'Change designation',
                    icon: <UserSquare size={14} />,
                  },
                  {
                    key: 'grade',
                    label: 'Change grade',
                    icon: <Star size={14} />,
                  },
                  {
                    key: 'manager',
                    label: 'Change manager',
                    icon: <UsersIcon size={14} />,
                  },
                ],
                onClick: ({ key }) => setChangeOpen(key as ChangeKind),
              } satisfies MenuProps
            }
          >
            <Button size="small" icon={<Pencil size={14} />}>
              <Space size={4}>
                Edit assignment
                <ChevronDown size={12} />
              </Space>
            </Button>
          </Dropdown>
        }
      >
        <div
          style={{
            display: 'grid',
            gridTemplateColumns: 'repeat(auto-fit, minmax(200px, 1fr))',
            gap: 'var(--app-space-md)',
          }}
        >
          <AssignmentTile
            icon={Network}
            label="Department"
            value={r?.department?.name}
            secondary={r?.department?.code}
          />
          <AssignmentTile
            icon={Network}
            label="Section"
            value={r?.section?.name}
            secondary={r?.section?.code}
          />
          <AssignmentTile
            icon={UserSquare}
            label="Designation"
            value={r?.designation?.title}
            secondary={r?.designation?.code}
          />
          <AssignmentTile
            icon={Star}
            label="Grade"
            value={r?.grade?.name}
            secondary={r?.grade?.code}
          />
          <AssignmentTile
            icon={UsersIcon}
            label="Manager"
            value={r?.manager?.fullName}
            secondary={r?.manager?.employeeCode}
          />
        </div>
      </Section>

      <Section
        title="Contract history"
        subtitle="Probation rows mark the active probation period; permanent rows the post-confirmation tenure."
      >
        <RequestState
          loading={contractsQuery.isLoading}
          error={contractsQuery.isError ? contractsQuery.error : undefined}
          loadingFallback={<Skeleton paragraph={{ rows: 3 }} />}
          errorTitle="Couldn't load contracts"
        >
          <ContractTable contracts={contractsQuery.data ?? []} />
        </RequestState>
      </Section>

      <Section
        title="Org-assignment history"
        subtitle="Every change to department, section, designation, grade, or manager."
      >
        <RequestState
          loading={historyQuery.isLoading}
          error={historyQuery.isError ? historyQuery.error : undefined}
          loadingFallback={<Skeleton paragraph={{ rows: 3 }} />}
          errorTitle="Couldn't load history"
        >
          <HistoryTable history={historyQuery.data?.content ?? []} />
        </RequestState>
      </Section>

      <Section
        title="Direct reports"
        subtitle={
          teamSearch
            ? `${teamQuery.data?.totalElements ?? 0} matching "${teamSearch}".`
            : `${teamQuery.data?.totalElements ?? e?.directReportsCount ?? 0} reporting in.`
        }
        actions={
          <Input
            allowClear
            prefix={<Search size={14} />}
            placeholder="Search name, code, email, phone, CNIC"
            value={teamSearchInput}
            onChange={(ev) => {
              setTeamSearchInput(ev.target.value);
              setTeamPage(1);
            }}
            style={{ width: 320 }}
          />
        }
      >
        <RequestState
          loading={teamQuery.isLoading}
          error={teamQuery.isError ? teamQuery.error : undefined}
          empty={
            !teamQuery.isLoading &&
            (teamQuery.data?.totalElements ?? 0) === 0
          }
          loadingFallback={<Skeleton paragraph={{ rows: 4 }} />}
          errorTitle="Couldn't load team"
          emptyTitle={teamSearch ? 'No matching reports' : 'No direct reports'}
        >
          <Table<EmployeeDto>
            rowKey={(r) => r.id ?? r.employeeCode ?? ''}
            size="middle"
            columns={teamColumns}
            dataSource={teamQuery.data?.content ?? []}
            loading={teamQuery.isFetching}
            // Scroll wide columns inside the table, not the page.
            scroll={{ x: 'max-content' }}
            pagination={{
              current: teamPage,
              pageSize: teamPageSize,
              total: teamQuery.data?.totalElements ?? 0,
              showSizeChanger: true,
              pageSizeOptions: [10, 20, 50, 100],
              showTotal: (total, [from, to]) => `${from}–${to} of ${total}`,
              onChange: (p, ps) => {
                setTeamPage(p);
                setTeamPageSize(ps);
              },
            }}
          />
        </RequestState>
      </Section>

      {/* `#warnings` is the character-certificate 422 link target. */}
      <div id="warnings">
        <Section
          title="Warnings"
          subtitle="Disciplinary actions on file. Issued rows wait on a decision; upheld rows count toward FY totals."
        >
          <WarningsPanel employeeId={employeeId} />
        </Section>
      </div>

      {e && changeOpen ? (
        <ChangeAssignmentModal
          kind={changeOpen}
          employeeId={employeeId}
          currentValueId={
            changeOpen === 'department'
              ? e.departmentId
              : changeOpen === 'section'
                ? e.sectionId
                : changeOpen === 'designation'
                  ? e.designationId
                  : changeOpen === 'grade'
                    ? e.gradeId
                    : e.managerId
          }
          currentValueLabel={
            changeOpen === 'department'
              ? detail?.resolved?.department?.name
              : changeOpen === 'section'
                ? detail?.resolved?.section?.name
                : changeOpen === 'designation'
                  ? detail?.resolved?.designation?.title
                  : changeOpen === 'grade'
                    ? detail?.resolved?.grade?.name
                    : detail?.resolved?.manager?.fullName
                      ? `${detail.resolved.manager.fullName}${
                          detail.resolved.manager.employeeCode
                            ? ` · ${detail.resolved.manager.employeeCode}`
                            : ''
                        }`
                      : undefined
          }
          currentDepartmentId={e.departmentId}
          onClose={() => setChangeOpen(null)}
        />
      ) : null}
    </div>
  );
}
