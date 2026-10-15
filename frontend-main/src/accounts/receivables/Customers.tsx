import { Space, Table, Tag, Typography } from 'antd';
import type { ColumnsType } from 'antd/es/table';

import { MoneyCell, PageHeader, Section } from '@/components';

import { PreviewNotice } from '../PreviewNotice';
import { customerInvoices, topCustomers } from '../mock/data';

const { Text } = Typography;

interface CustomerRow {
  name: string;
  ytdRevenue: number;
  share: number;
  openInvoices: number;
  outstanding: number;
}

function buildRows(): CustomerRow[] {
  return topCustomers
    .filter((c) => !c.name.startsWith('Other'))
    .map((c) => {
      const invs = customerInvoices.filter((i) => i.customer === c.name && i.status !== 'PAID');
      return {
        name: c.name,
        ytdRevenue: c.amount,
        share: c.share,
        openInvoices: invs.length,
        outstanding: invs.reduce((s, i) => s + (i.amount - i.paid), 0),
      };
    });
}

export function Customers() {
  const rows = buildRows();
  const columns: ColumnsType<CustomerRow> = [
    { title: 'Customer', dataIndex: 'name', key: 'name', render: (v: string) => <Text strong>{v}</Text> },
    {
      title: 'YTD revenue',
      dataIndex: 'ytdRevenue',
      key: 'ytdRevenue',
      width: 200,
      align: 'right',
      render: (v: number) => <MoneyCell amount={v} compact />,
    },
    {
      title: 'Share',
      dataIndex: 'share',
      key: 'share',
      width: 120,
      align: 'right',
      render: (v: number) => <Tag color={v > 0.15 ? 'gold' : 'blue'}>{(v * 100).toFixed(1)}%</Tag>,
    },
    { title: 'Open invoices', dataIndex: 'openInvoices', key: 'openInvoices', width: 140, align: 'right' },
    {
      title: 'Outstanding',
      dataIndex: 'outstanding',
      key: 'outstanding',
      width: 180,
      align: 'right',
      render: (v: number) => <MoneyCell amount={v} compact />,
    },
  ];

  return (
    <Space orientation="vertical" size={20} style={{ width: '100%' }}>
      <PageHeader
        eyebrow="RECEIVABLES"
        title="Customers"
        description="YTD revenue and open balance by customer — concentration risk at a glance."
      />
      <PreviewNotice />
      <Section title="Top customers">
        <Table<CustomerRow> rowKey="name" columns={columns} dataSource={rows} pagination={false} size="middle" />
      </Section>
    </Space>
  );
}
