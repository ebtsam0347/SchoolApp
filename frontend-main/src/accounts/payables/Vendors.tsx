import { Space, Table, Tag, Typography } from 'antd';
import type { ColumnsType } from 'antd/es/table';

import { MoneyCell, PageHeader, Section } from '@/components';

import { PreviewNotice } from '../PreviewNotice';
import { vendorBills } from '../mock/data';

const { Text } = Typography;

interface VendorRow {
  vendor: string;
  bills: number;
  outstanding: number;
  overdue: number;
}

function buildRows(): VendorRow[] {
  const map = new Map<string, VendorRow>();
  for (const b of vendorBills) {
    const r = map.get(b.vendor) ?? { vendor: b.vendor, bills: 0, outstanding: 0, overdue: 0 };
    r.bills += 1;
    if (b.status !== 'PAID') r.outstanding += b.amount;
    if (b.status === 'OVERDUE') r.overdue += b.amount;
    map.set(b.vendor, r);
  }
  return Array.from(map.values()).sort((a, b) => b.outstanding - a.outstanding);
}

export function Vendors() {
  const rows = buildRows();
  const columns: ColumnsType<VendorRow> = [
    { title: 'Vendor', dataIndex: 'vendor', key: 'vendor', render: (v: string) => <Text strong>{v}</Text> },
    { title: 'Bills', dataIndex: 'bills', key: 'bills', width: 100, align: 'right' },
    { title: 'Outstanding', dataIndex: 'outstanding', key: 'outstanding', width: 200, align: 'right', render: (v: number) => <MoneyCell amount={v} compact /> },
    {
      title: 'Overdue',
      dataIndex: 'overdue',
      key: 'overdue',
      width: 200,
      align: 'right',
      render: (v: number) =>
        v > 0 ? (
          <Tag color="red">
            <MoneyCell amount={v} compact />
          </Tag>
        ) : (
          <Text type="secondary" style={{ fontSize: 12 }}>
            —
          </Text>
        ),
    },
  ];

  return (
    <Space orientation="vertical" size={20} style={{ width: '100%' }}>
      <PageHeader eyebrow="PAYABLES" title="Vendors" description="Aggregated open exposure by vendor." />
      <PreviewNotice />
      <Section title="By outstanding">
        <Table<VendorRow> rowKey="vendor" columns={columns} dataSource={rows} pagination={false} size="middle" />
      </Section>
    </Space>
  );
}
