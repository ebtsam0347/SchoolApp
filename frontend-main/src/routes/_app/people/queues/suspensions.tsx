// /people/queues/suspensions — every employee currently suspended.
// Driven by /employees/directory with status=SUSPENDED. Lift action
// opens the LiftSuspensionModal from the shared action-modals module.

import { useState } from 'react';
import { createFileRoute } from '@tanstack/react-router';
import { useQuery } from '@tanstack/react-query';
import {
  Button,
  Table,
  Typography,
  type TableProps,
} from 'antd';
import { ShieldX } from 'lucide-react';

import { PersonCell, StatCard, StatusPill } from '@/components';
import { Can } from '@/auth';
import {
  EmployeeActionModal,
  type EmployeeActionKey,
} from '@/people/action-modals';
import {
  peopleBff,
  type EmployeeDirectoryRow,
  type EmployeeDto,
} from '@/people';
import { QueuePage } from '@/people/queues';

const { Text } = Typography;

export const Route = createFileRoute('/_app/people/queues/suspensions')({
  component: SuspensionsQueue,
});

function SuspensionsQueue() {
  const [acting, setActing] = useState<{
    employee: EmployeeDto;
    action: EmployeeActionKey;
  } | null>(null);

  const statsQuery = useQuery({
    queryKey: ['people', 'stats'],
    queryFn: ({ signal }) => peopleBff.stats(signal),
  });

  const queueQuery = useQuery({
    queryKey: ['people', 'queue', 'suspensions'],
    queryFn: ({ signal }) =>
      peopleBff.directory({ status: 'SUSPENDED', size: 100 }, signal),
  });

  const columns: TableProps<EmployeeDirectoryRow>['columns'] = [
    {
      title: 'Employee',
      key: 'employee',
      render: (_v, row) => {
        const e = row.employee;
        if (!e?.id) return null;
        return (
          <PersonCell
            person={{
              fullName: e.fullName ?? '—',
              employeeCode: e.employeeCode,
              designation: row.resolved?.designation?.title ?? null,
              profilePictureUrl: e.profilePictureUrl,
            }}
            to={`/people/${e.id}`}
          />
        );
      },
    },
    {
      title: 'Department',
      key: 'department',
      render: (_v, row) =>
        row.resolved?.department?.name ?? <Text type="secondary">—</Text>,
    },
    {
      title: 'Manager',
      key: 'manager',
      render: (_v, row) =>
        row.resolved?.manager?.fullName ?? <Text type="secondary">—</Text>,
    },
    {
      title: 'Status',
      key: 'status',
      render: () => <StatusPill tone="error">Suspended</StatusPill>,
    },
    {
      title: '',
      key: 'actions',
      render: (_v, row) => (
        <Can I="write" a="employee">
          <Button
            size="small"
            onClick={() =>
              row.employee &&
              setActing({ employee: row.employee, action: 'lift' })
            }
          >
            Lift suspension
          </Button>
        </Can>
      ),
    },
  ];

  return (
    <QueuePage
      title="Suspensions"
      description="Employees currently suspended. Lift the suspension to restore prior status."
      onRefresh={() => queueQuery.refetch()}
      isFetching={queueQuery.isFetching}
      kpis={
        <StatCard
          variant="hero"
          label="Currently suspended"
          value={statsQuery.data?.byStatus?.SUSPENDED ?? 0}
          delta="employees"
          icon={ShieldX}
        />
      }
      error={queueQuery.isError ? queueQuery.error : undefined}
    >
      <Table<EmployeeDirectoryRow>
        rowKey={(row) => String(row.employee?.id ?? Math.random())}
        loading={queueQuery.isLoading}
        dataSource={queueQuery.data?.content ?? []}
        columns={columns}
        size="middle"
        pagination={false}
        scroll={{ x: 'max-content' }}
      />

      {acting ? (
        <EmployeeActionModal
          action={acting.action}
          employee={acting.employee}
          onClose={() => setActing(null)}
        />
      ) : null}
    </QueuePage>
  );
}
