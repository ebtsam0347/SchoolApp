// Off-cycle payments table — last 12 months of cash extras, special OT,
// ad-hoc bonuses. Recorded against the employee, not on payslips.

import { Table, Tag, Typography, type TableColumnsType } from 'antd';
import { KpiGrid, MoneyCell, Section, StatCard } from '@/components';
import {
  formatDateShort,
  type EmployeeSalaryTab,
} from '@/payroll';
import { useSalaryTab } from './query-keys';

const { Text } = Typography;

export function OffCycleSection({ employeeId }: { employeeId: number }) {
  const tabQuery = useSalaryTab(employeeId);

  const rows = tabQuery.data?.offCycle12Months ?? [];
  const total = rows.reduce((s, r) => s + r.amount, 0);

  const columns: TableColumnsType<EmployeeSalaryTab['offCycle12Months'][number]> = [
    {
      title: 'Paid on',
      dataIndex: 'paidOn',
      key: 'paidOn',
      render: (v: string) => formatDateShort(v),
    },
    {
      title: 'Category',
      dataIndex: 'offCyclePaymentTypeName',
      key: 'category',
      render: (v: string | null) => v ?? '—',
    },
    {
      title: 'Amount',
      dataIndex: 'amount',
      key: 'amount',
      align: 'right',
      render: (v: number) => <MoneyCell amount={v} />,
    },
    {
      title: 'Reason',
      dataIndex: 'reason',
      key: 'reason',
      ellipsis: true,
    },
    {
      title: 'Source',
      key: 'source',
      render: (_v, r) =>
        r.sourceEventId ? <Tag color="purple">Bonus</Tag> : <Tag>HR</Tag>,
    },
  ];

  return (
    <Section
      title="Off-cycle payments (last 12 months)"
      subtitle="Cash extras, special OT, ad-hoc bonuses. Recorded against the employee, not on payslips."
    >
      {rows.length === 0 && !tabQuery.isLoading ? (
        <Text type="secondary">No off-cycle payments in the last year.</Text>
      ) : (
        <>
          <KpiGrid>
            <StatCard
              label="Total (12mo)"
              value={<MoneyCell amount={total} compact />}
              tone="primary"
            />
            <StatCard label="Rows" value={rows.length.toString()} tone="info" />
          </KpiGrid>
          <Table
            rowKey="id"
            loading={tabQuery.isLoading}
            dataSource={rows}
            columns={columns}
            pagination={false}
            size="small"
            // Scroll wide columns inside the table, not the page.
            scroll={{ x: 'max-content' }}
            style={{ marginTop: 'var(--app-space-md)' }}
          />
        </>
      )}
    </Section>
  );
}
