// Allowances + deductions panel. Two sub-tables (earnings and
// deductions), three KPIs at the top (totals + line counts), and the
// add/edit modals. Each line is effective-dated; updating an amount
// closes the current row and inserts a new one.

import { Can } from '@/auth';
import { EmptyState, MoneyCell, Section, StatusPill } from '@/components';
import { getErrorMessage } from '@/lib';
import {
  COMPONENT_TYPE_LABEL,
  COMPONENT_TYPE_TONE,
  employeeSalaryLinesApi,
  formatDateShort,
  type ComponentType,
  type EmployeeSalaryComponentDto,
} from '@/payroll';
import { useMutation, useQueryClient } from '@tanstack/react-query';
import {
  App as AntdApp,
  Button,
  Empty,
  Popconfirm,
  Space,
  Table,
  Typography,
  type TableColumnsType,
} from 'antd';
import dayjs from 'dayjs';
import { Plus, Trash2 } from 'lucide-react';
import { useState } from 'react';
import { AddLineModal, EditLineModal } from './ComponentLineModals';
import { salaryTabKey, useSalaryTab } from './query-keys';

const { Text } = Typography;

export function ComponentsSection({ employeeId }: { employeeId: number }) {
  const tabQuery = useSalaryTab(employeeId);

  const [openAdd, setOpenAdd] = useState(false);
  const [editing, setEditing] = useState<EmployeeSalaryComponentDto | null>(null);
  const queryClient = useQueryClient();
  const { message } = AntdApp.useApp();

  const lines = tabQuery.data?.components ?? [];
  const catalog = tabQuery.data?.catalog ?? [];
  const earnings = lines.filter((l) => l.type === 'EARNING');
  const deductions = lines.filter((l) => l.type === 'DEDUCTION');

  const remove = useMutation({
    mutationFn: ({ lineId, effectiveTo }: { lineId: number; effectiveTo: string }) =>
      employeeSalaryLinesApi.remove(employeeId, lineId, { effectiveTo }),
    onSuccess: () => {
      void queryClient.invalidateQueries({ queryKey: salaryTabKey(employeeId) });
      message.success('Line stopped.');
    },
    onError: (e) => message.error(getErrorMessage(e)),
  });

  function buildColumns(): TableColumnsType<EmployeeSalaryComponentDto> {
    return [
      {
        title: 'Component',
        key: 'component',
        render: (_v, r) => (
          <Space size={4} orientation="vertical">
            <span style={{ fontWeight: 500 }}>{r.componentName}</span>
            <Text type="secondary" style={{ fontSize: 11 }}>
              {r.componentCode}
              {r.calcType === 'PER_DAY' ? ' · per day' : ''}
            </Text>
          </Space>
        ),
      },
      {
        title: 'Amount',
        dataIndex: 'amount',
        key: 'amount',
        align: 'right',
        render: (v: number) => <MoneyCell amount={v} />,
      },
      {
        title: 'Effective from',
        dataIndex: 'effectiveFrom',
        key: 'effectiveFrom',
        render: (v: string) => formatDateShort(v),
      },
      {
        title: 'Calculate with salary',
        dataIndex: 'calculateWithSalary',
        key: 'calculateWithSalary',
        align: 'center',
        width: 150,
        render: (v: boolean) => (
          <StatusPill tone={v ? 'success' : 'neutral'}>
            {v ? 'Yes' : 'No'}
          </StatusPill>
        ),
      },
      {
        title: 'Notes',
        dataIndex: 'notes',
        key: 'notes',
        ellipsis: true,
      },
      {
        title: '',
        key: 'actions',
        render: (_v, row) => (
          <Can I="write" a="payroll">
            <Space size={4}>
              <Button size="small" onClick={() => setEditing(row)}>
                Edit
              </Button>
              <Popconfirm
                title={`Stop ${row.componentCode} as of today?`}
                description={
                  row.amount != null
                    ? `Rs ${row.amount.toLocaleString()} — effective from ${formatDateShort(row.effectiveFrom)}`
                    : undefined
                }
                onConfirm={() =>
                  remove.mutateAsync({
                    lineId: row.id,
                    effectiveTo: dayjs().format('YYYY-MM-DD'),
                  })
                }
              >
                <Button size="small" danger icon={<Trash2 size={12} strokeWidth={1.75} />}>
                  Stop
                </Button>
              </Popconfirm>
            </Space>
          </Can>
        ),
      },
    ];
  }

  return (
    <Section
      title="Allowances & deductions"
      subtitle="Each line picks from the global salary-component catalog. Updating an amount closes the current row and inserts a new one."
      actions={
        <Can I="write" a="payroll">
          <Button
            icon={<Plus size={14} strokeWidth={1.75} />}
            onClick={() => setOpenAdd(true)}
          >
            Add line
          </Button>
        </Can>
      }
    >
      {lines.length === 0 && !tabQuery.isLoading ? (
        <EmptyState
          title="No salary components on file"
          description="Add HRA, conveyance, income-tax, and any deductions from the catalog."
        />
      ) : (
        <>

          <div
            style={{
              display: 'flex',
              flexDirection: 'column',
              gap: 12,
              marginTop: 'var(--app-space-md)',
            }}
          >
            <SubTable
              title="Earnings"
              tone="EARNING"
              rows={earnings}
              columns={buildColumns()}
            />
            <SubTable
              title="Deductions"
              tone="DEDUCTION"
              rows={deductions}
              columns={buildColumns()}
            />
          </div>
        </>
      )}

      <AddLineModal
        employeeId={employeeId}
        open={openAdd}
        onClose={() => setOpenAdd(false)}
        catalog={catalog}
      />
      <EditLineModal
        employeeId={employeeId}
        line={editing}
        onClose={() => setEditing(null)}
      />
    </Section>
  );
}

function SubTable({
  title,
  tone,
  rows,
  columns,
}: {
  title: string;
  tone: ComponentType;
  rows: EmployeeSalaryComponentDto[];
  columns: TableColumnsType<EmployeeSalaryComponentDto>;
}) {
  return (
    <div>
      <Space size={6} style={{ marginBottom: 6 }}>
        <StatusPill tone={COMPONENT_TYPE_TONE[tone]}>
          {COMPONENT_TYPE_LABEL[tone]}
        </StatusPill>
        <Text type="secondary" style={{ fontSize: 12 }}>
          {title} · {rows.length}
        </Text>
      </Space>
      <Table<EmployeeSalaryComponentDto>
        rowKey="id"
        dataSource={rows}
        columns={columns}
        pagination={false}
        size="small"
        // Scroll wide columns inside the table, not the page.
        scroll={{ x: 'max-content' }}
        locale={{ emptyText: <Empty description={`No ${title.toLowerCase()}`} /> }}
      />
    </div>
  );
}
