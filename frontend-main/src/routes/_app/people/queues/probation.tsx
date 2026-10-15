// /people/queues/probation — HR queue of employees with probation due
// soon or already overdue. Driven by /employees/directory with
// probationDueWithin / probationOverdue. Inline Confirm / Extend actions
// open the same modals used on the detail page.

import { useState } from 'react';
import { createFileRoute, useNavigate } from '@tanstack/react-router';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import {
  App as AntdApp,
  Button,
  InputNumber,
  Popconfirm,
  Segmented,
  Space,
  Table,
  Typography,
  type TableProps,
} from 'antd';
import { CalendarClock, CheckCheck } from 'lucide-react';

import {
  ExpiryBadge,
  PersonCell,
  SelectionToolbar,
  StatCard,
} from '@/components';
import { Can } from '@/auth';
import { formatDateShort, getErrorMessage } from '@/lib';
import {
  EmployeeActionModal,
  type EmployeeActionKey,
} from '@/people/action-modals';
import {
  employeesApi,
  peopleBff,
  type EmployeeDirectoryRow,
  type EmployeeDto,
} from '@/people';
import { QueuePage } from '@/people/queues';

const { Text } = Typography;

type Mode = 'due' | 'overdue';

export const Route = createFileRoute('/_app/people/queues/probation')({
  component: ProbationQueue,
});

function ProbationQueue() {
  const navigate = useNavigate();
  const queryClient = useQueryClient();
  const { message } = AntdApp.useApp();
  const [mode, setMode] = useState<Mode>('due');
  const [days, setDays] = useState(14);
  const [acting, setActing] = useState<{
    employee: EmployeeDto;
    action: EmployeeActionKey;
  } | null>(null);
  const [selectedIds, setSelectedIds] = useState<number[]>([]);

  const statsQuery = useQuery({
    queryKey: ['people', 'stats'],
    queryFn: ({ signal }) => peopleBff.stats(signal),
  });

  const queueQuery = useQuery({
    queryKey: ['people', 'queue', 'probation', mode, mode === 'due' ? days : undefined],
    queryFn: ({ signal }) =>
      peopleBff.directory(
        mode === 'due'
          ? { probationDueWithin: days, size: 100, sort: 'joiningDate,asc' }
          : { probationOverdue: true, size: 100, sort: 'joiningDate,asc' },
        signal,
      ),
  });

  const stats = statsQuery.data?.probation;
  const rows = queueQuery.data?.content ?? [];

  const bulkConfirm = useMutation({
    mutationFn: async (ids: number[]) => {
      const results = await Promise.allSettled(
        ids.map((id) => employeesApi.confirm(id, {})),
      );
      return {
        succeeded: results.filter((r) => r.status === 'fulfilled').length,
        failed: results
          .map((r, i) => ({ result: r, id: ids[i]! }))
          .filter((x) => x.result.status === 'rejected'),
      };
    },
    onSuccess: ({ succeeded, failed }) => {
      if (failed.length === 0) {
        message.success(`Confirmed ${succeeded} ${succeeded === 1 ? 'employee' : 'employees'}.`);
      } else if (succeeded === 0) {
        message.error(`Couldn't confirm any. ${failed.length} failed.`);
      } else {
        message.warning(
          `Confirmed ${succeeded}. ${failed.length} failed — try those individually.`,
        );
      }
      setSelectedIds([]);
      void queryClient.invalidateQueries({ queryKey: ['people'] });
    },
    onError: (err) => message.error(getErrorMessage(err)),
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
      title: 'Joined',
      key: 'joined',
      render: (_v, row) =>
        row.employee?.joiningDate ? formatDateShort(row.employee.joiningDate) : '—',
    },
    {
      title: 'Probation due',
      key: 'due',
      render: (_v, row) => {
        const d = row.employee?.activeProbation?.endDate;
        if (!d) return <Text type="secondary">—</Text>;
        return (
          <span style={{ display: 'inline-flex', gap: 8, alignItems: 'center' }}>
            {formatDateShort(d)}
            <ExpiryBadge date={d} mode="deadline" />
          </span>
        );
      },
    },
    {
      title: '',
      key: 'actions',
      render: (_v, row) => {
        if (!row.employee) return null;
        return (
          <Can I="write" a="employee">
            <Space>
              <Button
                size="small"
                onClick={() =>
                  setActing({ employee: row.employee!, action: 'confirm' })
                }
              >
                Confirm
              </Button>
              {!row.employee.activeProbation?.isExtended ? (
                <Button
                  size="small"
                  type="link"
                  onClick={() =>
                    setActing({ employee: row.employee!, action: 'extend' })
                  }
                >
                  Extend
                </Button>
              ) : null}
            </Space>
          </Can>
        );
      },
    },
  ];

  return (
    <QueuePage
      title="Probation"
      description="Confirm or extend before the probation date passes. Overdue probations stay active until HR resolves them explicitly."
      onRefresh={() => queueQuery.refetch()}
      isFetching={queueQuery.isFetching}
      headerActions={
        <Button onClick={() => navigate({ to: '/people' })}>Back to People</Button>
      }
      kpis={
        <>
          <StatCard
            variant="hero"
            label="Active probations"
            value={stats?.active ?? 0}
            delta="Across the workforce"
            icon={CalendarClock}
          />
          <StatCard
            variant="accent-corner"
            tone="warning"
            accentCorner="tr"
            label="Due within 14 days"
            value={stats?.dueWithin14 ?? 0}
            icon={CalendarClock}
          />
          <StatCard
            variant="accent-radial"
            tone="error"
            accentCorner="tr"
            label="Overdue"
            value={stats?.overdue ?? 0}
            delta="Past the probation date"
            icon={CalendarClock}
          />
        </>
      }
      filters={
        <div
          style={{
            display: 'flex',
            alignItems: 'center',
            gap: 16,
            flexWrap: 'wrap',
          }}
        >
          <Segmented<Mode>
            value={mode}
            onChange={setMode}
            options={[
              { label: 'Due soon', value: 'due' },
              { label: 'Overdue', value: 'overdue' },
            ]}
          />
          {mode === 'due' ? (
            <span style={{ display: 'inline-flex', alignItems: 'center', gap: 8 }}>
              <Text type="secondary" style={{ fontSize: 13 }}>
                Within
              </Text>
              <InputNumber
                min={1}
                max={120}
                value={days}
                onChange={(v) => setDays(v ?? 14)}
                style={{ width: 80 }}
                suffix="days"
              />
            </span>
          ) : null}
        </div>
      }
      error={queueQuery.isError ? queueQuery.error : undefined}
    >
      <Table<EmployeeDirectoryRow>
        rowKey={(row) => String(row.employee?.id ?? Math.random())}
        loading={queueQuery.isLoading}
        dataSource={rows}
        columns={columns}
        size="middle"
        pagination={false}
        scroll={{ x: 'max-content' }}
        rowSelection={{
          selectedRowKeys: selectedIds.map(String),
          onChange: (keys) => setSelectedIds(keys.map((k) => Number(k))),
          getCheckboxProps: (row) => ({ disabled: !row.employee?.id }),
        }}
      />

      <Can I="write" a="employee">
        <SelectionToolbar
          selectedCount={selectedIds.length}
          totalCount={rows.length}
          onClear={() => setSelectedIds([])}
          actions={
            <Popconfirm
              title={`Confirm ${selectedIds.length} ${selectedIds.length === 1 ? 'employee' : 'employees'}?`}
              description="Each active probation will be marked complete and a permanent contract created from today."
              okText="Yes, confirm all"
              onConfirm={() => bulkConfirm.mutate(selectedIds)}
            >
              <Button
                type="primary"
                icon={<CheckCheck size={14} strokeWidth={1.75} />}
                loading={bulkConfirm.isPending}
              >
                Confirm {selectedIds.length}
              </Button>
            </Popconfirm>
          }
        />
      </Can>

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
