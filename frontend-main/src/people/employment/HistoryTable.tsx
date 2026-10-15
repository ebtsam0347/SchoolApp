// Org-assignment history table — every change to department, section,
// designation, grade, or manager. The BFF resolves catalog labels and
// manager summaries so this table can render names instead of FK ids.

import { Table, Tag, Typography, type TableProps } from 'antd';
import type { EmployeeHistoryRow } from '@/people';
import { formatDate } from './format';

const { Text } = Typography;

export function HistoryTable({ history }: { history: EmployeeHistoryRow[] }) {
  // BFF returns newest-first already; trust the order.
  const columns: TableProps<EmployeeHistoryRow>['columns'] = [
    {
      title: 'Effective',
      key: 'effective',
      width: 220,
      render: (_v, row) => (
        <span style={{ display: 'inline-flex', gap: 8, alignItems: 'center' }}>
          {row.effectiveFrom ? formatDate(row.effectiveFrom) : '—'}
          {row.effectiveTo ? (
            <Text type="secondary">→ {formatDate(row.effectiveTo)}</Text>
          ) : (
            <Tag color="success" style={{ margin: 0 }}>
              Current
            </Tag>
          )}
        </span>
      ),
    },
    {
      title: 'Reason',
      dataIndex: 'changeReason',
      key: 'changeReason',
      width: 180,
      render: (v?: string) => {
        if (!v) return null;
        // BE codes are SCREAMING_SNAKE_CASE (ONBOARD, REORG, …); render as
        // Sentence case so the Reason chip matches the rest of the page's
        // pill casing.
        const pretty = v
          .replace(/_/g, ' ')
          .toLowerCase()
          .replace(/^(.)/, (c) => c.toUpperCase());
        return <Tag>{pretty}</Tag>;
      },
    },
    {
      title: 'Department',
      key: 'department',
      render: (_v, row) =>
        row.department?.name ?? <Text type="secondary">—</Text>,
    },
    {
      title: 'Section',
      key: 'section',
      render: (_v, row) => row.section?.name ?? <Text type="secondary">—</Text>,
    },
    {
      title: 'Designation',
      key: 'designation',
      render: (_v, row) =>
        row.designation?.title ?? <Text type="secondary">—</Text>,
    },
    {
      title: 'Grade',
      key: 'grade',
      render: (_v, row) => row.grade?.name ?? <Text type="secondary">—</Text>,
    },
    {
      title: 'Manager',
      key: 'manager',
      render: (_v, row) =>
        row.manager?.fullName ?? <Text type="secondary">—</Text>,
    },
    {
      title: 'Notes',
      dataIndex: 'notes',
      key: 'notes',
      ellipsis: true,
    },
  ];

  return (
    <Table<EmployeeHistoryRow>
      rowKey={(row) => String(row.id ?? Math.random())}
      dataSource={history}
      columns={columns}
      pagination={{ pageSize: 8, showSizeChanger: false }}
      size="small"
      scroll={{ x: 'max-content' }}
    />
  );
}
