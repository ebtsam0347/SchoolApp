// Contract history table — probation rows mark the active probation
// period; permanent rows the post-confirmation tenure.

import { Table, Tag, Typography, type TableProps } from 'antd';
import { ExternalLink, FileText } from 'lucide-react';
import { ExpiryBadge, StatusPill } from '@/components';
import {
  CONTRACT_TYPE_LABEL,
  type EmploymentContractDto,
} from '@/people';
import { formatDate } from './format';

const { Text } = Typography;

export function ContractTable({
  contracts,
}: {
  contracts: EmploymentContractDto[];
}) {
  const columns: TableProps<EmploymentContractDto>['columns'] = [
    {
      title: 'Type',
      dataIndex: 'contractType',
      key: 'type',
      render: (v: EmploymentContractDto['contractType']) =>
        v ? CONTRACT_TYPE_LABEL[v] : '—',
    },
    {
      title: 'Started',
      dataIndex: 'startDate',
      key: 'startDate',
      render: formatDate,
    },
    {
      title: 'Ends',
      dataIndex: 'endDate',
      key: 'endDate',
      render: (v: string | undefined, row) => {
        if (!v) return <Text type="secondary">—</Text>;
        return (
          <span style={{ display: 'inline-flex', gap: 8, alignItems: 'center' }}>
            {formatDate(v)}
            {row.contractType === 'PROBATION' ? (
              <ExpiryBadge date={v} mode="deadline" />
            ) : null}
          </span>
        );
      },
    },
    {
      title: 'Status',
      dataIndex: 'status',
      key: 'status',
      render: (v: string | undefined) => {
        if (!v) return null;
        const tone = v === 'ACTIVE' ? 'success' : v === 'EXPIRED' ? 'neutral' : 'error';
        const label = v.charAt(0) + v.slice(1).toLowerCase();
        return <StatusPill tone={tone}>{label}</StatusPill>;
      },
    },
    {
      title: 'Extended',
      dataIndex: 'extended',
      key: 'extended',
      render: (v?: boolean) => (v ? <Tag color="warning">Extended</Tag> : null),
    },
    {
      title: 'Document',
      key: 'document',
      render: (_v, row) =>
        row.documentUrl ? (
          <a
            href={row.documentUrl}
            target="_blank"
            rel="noreferrer"
            style={{ display: 'inline-flex', alignItems: 'center', gap: 4 }}
          >
            <FileText size={14} /> Open <ExternalLink size={12} />
          </a>
        ) : (
          <Text type="secondary">—</Text>
        ),
    },
  ];

  return (
    <Table<EmploymentContractDto>
      rowKey={(row) => String(row.id ?? Math.random())}
      dataSource={contracts}
      columns={columns}
      pagination={false}
      size="small"
      // Keep the columns scrolling inside the table on a phone rather than
      // dragging the page sideways. No-op on a wide viewport.
      scroll={{ x: 'max-content' }}
    />
  );
}
