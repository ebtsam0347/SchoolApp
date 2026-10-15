import { Space, Table, Tag, Typography } from 'antd';
import type { ColumnsType } from 'antd/es/table';

import { MoneyCell, PageHeader, Section } from '@/components';

import { PreviewNotice } from '../PreviewNotice';

const { Text } = Typography;

interface RuleRow {
  section: string;
  description: string;
  rate: string;
  ytdWithheld: number;
  status: 'ACTIVE' | 'PAUSED';
}

const rows: RuleRow[] = [
  { section: '149', description: 'Salary — employees', rate: 'slab', ytdWithheld: 38_400_000, status: 'ACTIVE' },
  { section: '153(1)(a)', description: 'Goods — supplier payments', rate: '4.5% / 5.5%', ytdWithheld: 28_200_000, status: 'ACTIVE' },
  { section: '153(1)(b)', description: 'Services — vendors', rate: '8% / 11%', ytdWithheld: 6_180_000, status: 'ACTIVE' },
  { section: '155', description: 'Rent', rate: '5% / 7.5%', ytdWithheld: 1_240_000, status: 'ACTIVE' },
  { section: '236(K)', description: 'Property transfer', rate: '3% / 5%', ytdWithheld: 0, status: 'PAUSED' },
];

export function Withholding() {
  const columns: ColumnsType<RuleRow> = [
    { title: 'Section', dataIndex: 'section', key: 'section', width: 120, render: (v) => <Text strong>{v}</Text> },
    { title: 'Description', dataIndex: 'description', key: 'description' },
    { title: 'Rate', dataIndex: 'rate', key: 'rate', width: 160 },
    { title: 'YTD withheld', dataIndex: 'ytdWithheld', key: 'ytdWithheld', width: 200, align: 'right', render: (v: number) => <MoneyCell amount={v} compact /> },
    { title: 'Status', dataIndex: 'status', key: 'status', width: 110, render: (s: RuleRow['status']) => <Tag color={s === 'ACTIVE' ? 'green' : 'default'}>{s}</Tag> },
  ];

  return (
    <Space orientation="vertical" size={20} style={{ width: '100%' }}>
      <PageHeader
        eyebrow="COMPLIANCE"
        title="Withholding"
        description="Active withholding rules, applicable rates, and YTD amounts withheld for FBR challan filing."
      />
      <PreviewNotice />
      <Section title="Active rules">
        <Table<RuleRow> rowKey="section" columns={columns} dataSource={rows} pagination={false} size="middle" />
      </Section>
    </Space>
  );
}
