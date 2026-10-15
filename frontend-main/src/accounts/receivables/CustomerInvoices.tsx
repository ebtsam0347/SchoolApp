import { useState } from 'react';
import { Button, Progress, Space, Table, Tag, Typography, theme as antdTheme } from 'antd';
import type { ColumnsType } from 'antd/es/table';
import { AlertTriangle, FilePlus2, Receipt, TrendingUp } from 'lucide-react';

import { FilterBar, KpiGrid, MoneyCell, PageHeader, Section, StatCard } from '@/components';
import { formatDateShort } from '@/lib/dates';

import { PreviewNotice } from '../PreviewNotice';
import { customerInvoices, execKpis, type CustomerInvoice } from '../mock/data';

const { Text } = Typography;
const { useToken } = antdTheme;

const statusTone: Record<CustomerInvoice['status'], string> = {
  OPEN: 'blue',
  PARTIAL: 'gold',
  PAID: 'green',
  OVERDUE: 'red',
};

export function CustomerInvoices() {
  const { token } = useToken();
  const [search, setSearch] = useState('');

  const filtered = customerInvoices.filter((inv) => {
    if (!search.trim()) return true;
    const q = search.toLowerCase();
    return inv.customer.toLowerCase().includes(q) || inv.invoiceNumber.toLowerCase().includes(q);
  });

  const open = customerInvoices.filter((i) => i.status !== 'PAID').length;
  const overdue = customerInvoices.filter((i) => i.status === 'OVERDUE').length;
  const totalOpen = customerInvoices
    .filter((i) => i.status !== 'PAID')
    .reduce((s, i) => s + (i.amount - i.paid), 0);

  const columns: ColumnsType<CustomerInvoice> = [
    {
      title: 'Customer',
      dataIndex: 'customer',
      key: 'customer',
      render: (c: string, row) => (
        <Space orientation="vertical" size={0}>
          <Text strong>{c}</Text>
          <Text type="secondary" style={{ fontSize: 11, fontFamily: 'var(--font-mono, ui-monospace)' }}>
            {row.invoiceNumber}
          </Text>
        </Space>
      ),
    },
    {
      title: 'Issued',
      dataIndex: 'invoiceDate',
      key: 'invoiceDate',
      width: 140,
      render: (d: string) => formatDateShort(d),
    },
    {
      title: 'Due',
      dataIndex: 'dueDate',
      key: 'dueDate',
      width: 140,
      render: (d: string, row) => (
        <span style={{ color: row.status === 'OVERDUE' ? token.colorError : undefined }}>
          {formatDateShort(d)}
        </span>
      ),
    },
    {
      title: 'Amount',
      dataIndex: 'amount',
      key: 'amount',
      width: 140,
      align: 'right',
      render: (v: number) => <MoneyCell amount={v} compact />,
    },
    {
      title: 'Collected',
      key: 'collected',
      width: 200,
      render: (_, row) => {
        const pct = row.amount > 0 ? (row.paid / row.amount) * 100 : 0;
        return (
          <Space orientation="vertical" size={2} style={{ width: '100%' }}>
            <Progress
              percent={pct}
              size="small"
              showInfo={false}
              strokeColor={pct === 100 ? token.colorSuccess : token.colorPrimary}
            />
            <Text type="secondary" style={{ fontSize: 11 }}>
              <MoneyCell amount={row.paid} compact /> of <MoneyCell amount={row.amount} compact />
            </Text>
          </Space>
        );
      },
    },
    {
      title: 'Status',
      dataIndex: 'status',
      key: 'status',
      width: 110,
      render: (s: CustomerInvoice['status']) => <Tag color={statusTone[s]}>{s}</Tag>,
    },
  ];

  return (
    <Space orientation="vertical" size={20} style={{ width: '100%' }}>
      <PageHeader
        eyebrow="RECEIVABLES"
        title="Customer invoices"
        description="Outstanding invoices and collection state across all customers — track aging, partial receipts, and overdues."
        actions={
          <Button type="primary" icon={<FilePlus2 size={14} strokeWidth={1.75} />}>
            New invoice
          </Button>
        }
      />

      <PreviewNotice />

      <KpiGrid minColumnWidth={220}>
        <StatCard
          variant="accent-radial"
          tone="primary"
          icon={Receipt}
          label="Outstanding"
          value={<MoneyCell amount={execKpis.arOutstanding} compact />}
          delta={`${execKpis.arDays} days outstanding`}
          accentCorner="tr"
        />
        <StatCard
          variant="accent-radial"
          tone="info"
          icon={TrendingUp}
          label="Open invoices"
          value={open.toString()}
          accentCorner="tr"
        />
        <StatCard
          variant="accent-radial"
          tone="warning"
          icon={AlertTriangle}
          label="Overdue"
          value={overdue.toString()}
          delta="needs follow-up"
          accentCorner="tr"
        />
        <StatCard
          variant="accent-radial"
          tone="success"
          icon={Receipt}
          label="Visible balance"
          value={<MoneyCell amount={totalOpen} compact />}
          accentCorner="tr"
        />
      </KpiGrid>

      <Section title="Open & recent" subtitle="Latest invoices across customers">
        <FilterBar
          search={{
            value: search,
            onChange: setSearch,
            placeholder: 'Search customer or invoice…',
          }}
        />
        <Table<CustomerInvoice>
          rowKey="id"
          columns={columns}
          dataSource={filtered}
          pagination={false}
          size="middle"
          style={{ marginTop: 12 }}
        />
      </Section>
    </Space>
  );
}
