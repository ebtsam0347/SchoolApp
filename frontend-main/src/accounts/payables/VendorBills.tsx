import { useState } from 'react';
import { Button, Drawer, Space, Table, Tag, Typography, theme as antdTheme } from 'antd';
import type { ColumnsType } from 'antd/es/table';
import {
  AlertTriangle,
  CheckCircle2,
  Clock,
  FilePlus2,
  Wallet,
} from 'lucide-react';

import { FilterBar, KpiGrid, MoneyCell, PageHeader, Section, StatCard } from '@/components';
import { formatDateShort } from '@/lib/dates';
import { enumLabel } from '@/lib/strings';

import { PreviewNotice } from '../PreviewNotice';
import { vendorBills, type VendorBill } from '../mock/data';

const { Text, Title } = Typography;
const { useToken } = antdTheme;

const statusTone: Record<VendorBill['status'], string> = {
  DRAFT: 'default',
  AWAITING_APPROVAL: 'gold',
  APPROVED: 'blue',
  PAID: 'green',
  OVERDUE: 'red',
};

const matchTone: Record<VendorBill['matchStatus'], string> = {
  '3-way matched': 'green',
  'PO mismatch': 'orange',
  'GRN mismatch': 'red',
  'No PO': 'default',
};

export function VendorBills() {
  const { token } = useToken();
  const [search, setSearch] = useState('');
  const [active, setActive] = useState<VendorBill | null>(null);

  const filtered = vendorBills.filter((b) => {
    if (!search.trim()) return true;
    const q = search.toLowerCase();
    return (
      b.vendor.toLowerCase().includes(q) ||
      b.billNumber.toLowerCase().includes(q) ||
      (b.poNumber ?? '').toLowerCase().includes(q)
    );
  });

  const totalOutstanding = vendorBills
    .filter((b) => b.status !== 'PAID')
    .reduce((s, b) => s + b.amount, 0);
  const overdue = vendorBills.filter((b) => b.status === 'OVERDUE').length;
  const awaiting = vendorBills.filter((b) => b.status === 'AWAITING_APPROVAL').length;
  const dueThisWeek = vendorBills.filter((b) => {
    const due = new Date(b.dueDate);
    const now = new Date('2026-05-04');
    const diff = (due.getTime() - now.getTime()) / (1000 * 60 * 60 * 24);
    return diff >= 0 && diff <= 7 && b.status !== 'PAID';
  }).length;

  const columns: ColumnsType<VendorBill> = [
    {
      title: 'Vendor',
      dataIndex: 'vendor',
      key: 'vendor',
      render: (v: string, row) => (
        <Space orientation="vertical" size={0}>
          <Text strong>{v}</Text>
          <Text type="secondary" style={{ fontSize: 11, fontFamily: 'var(--font-mono, ui-monospace)' }}>
            {row.billNumber}
          </Text>
        </Space>
      ),
    },
    {
      title: 'Bill date',
      dataIndex: 'billDate',
      key: 'billDate',
      width: 140,
      render: (d: string) => formatDateShort(d),
    },
    {
      title: 'Due',
      dataIndex: 'dueDate',
      key: 'dueDate',
      width: 140,
      render: (d: string, row) => {
        const overdue = row.status === 'OVERDUE';
        return (
          <span style={{ color: overdue ? token.colorError : undefined }}>
            {formatDateShort(d)}
          </span>
        );
      },
    },
    {
      title: 'PO',
      dataIndex: 'poNumber',
      key: 'poNumber',
      width: 130,
      render: (p?: string) =>
        p ? (
          <Text style={{ fontFamily: 'var(--font-mono, ui-monospace)', fontSize: 12 }}>{p}</Text>
        ) : (
          <Text type="secondary" style={{ fontSize: 12 }}>
            —
          </Text>
        ),
    },
    {
      title: 'Match',
      dataIndex: 'matchStatus',
      key: 'matchStatus',
      width: 150,
      render: (m: VendorBill['matchStatus']) => <Tag color={matchTone[m]}>{m}</Tag>,
    },
    {
      title: 'Amount',
      dataIndex: 'amount',
      key: 'amount',
      width: 150,
      align: 'right',
      render: (v: number) => <MoneyCell amount={v} compact />,
    },
    {
      title: 'Status',
      dataIndex: 'status',
      key: 'status',
      width: 160,
      render: (s: VendorBill['status']) => (
        <Tag color={statusTone[s]}>{enumLabel(s)}</Tag>
      ),
    },
  ];

  return (
    <Space orientation="vertical" size={20} style={{ width: '100%' }}>
      <PageHeader
        eyebrow="PAYABLES"
        title="Vendor bills"
        description="Inbox of bills posted from vendors — ready for 3-way match, approval, and payment release."
        actions={
          <Button type="primary" icon={<FilePlus2 size={14} strokeWidth={1.75} />}>
            Capture bill
          </Button>
        }
      />

      <PreviewNotice />

      <KpiGrid minColumnWidth={220}>
        <StatCard
          variant="accent-radial"
          tone="primary"
          icon={Wallet}
          label="Outstanding"
          value={<MoneyCell amount={totalOutstanding} compact />}
          delta="across all open bills"
          accentCorner="tr"
        />
        <StatCard
          variant="accent-radial"
          tone="warning"
          icon={Clock}
          label="Awaiting approval"
          value={awaiting.toString()}
          delta="ready for sign-off"
          accentCorner="tr"
        />
        <StatCard
          variant="accent-radial"
          tone="info"
          icon={CheckCircle2}
          label="Due this week"
          value={dueThisWeek.toString()}
          delta="release window"
          accentCorner="tr"
        />
        <StatCard
          variant="accent-radial"
          tone="error"
          icon={AlertTriangle}
          label="Overdue"
          value={overdue.toString()}
          delta="needs immediate action"
          accentCorner="tr"
        />
      </KpiGrid>

      <Section title="Bills inbox" subtitle="Click a row for the full match detail">
        <FilterBar
          search={{
            value: search,
            onChange: setSearch,
            placeholder: 'Search vendor, bill, PO…',
          }}
        />
        <Table<VendorBill>
          rowKey="id"
          columns={columns}
          dataSource={filtered}
          pagination={false}
          size="middle"
          style={{ marginTop: 12 }}
          onRow={(record) => ({
            onClick: () => setActive(record),
            style: { cursor: 'pointer' },
          })}
        />
      </Section>

      <Drawer
        open={!!active}
        onClose={() => setActive(null)}
        title={active?.vendor}
        size="default"
        destroyOnHidden
      >
        {active ? (
          <Space orientation="vertical" size={16} style={{ width: '100%' }}>
            <div>
              <Text type="secondary" style={{ fontSize: 11, letterSpacing: '0.08em', textTransform: 'uppercase' }}>
                Bill {active.billNumber}
              </Text>
              <Title level={3} style={{ margin: '4px 0 0 0' }}>
                <MoneyCell amount={active.amount} compact />
              </Title>
            </div>

            <Tag color={matchTone[active.matchStatus]}>{active.matchStatus}</Tag>

            <div
              style={{
                padding: 14,
                background: token.colorFillTertiary,
                borderRadius: token.borderRadiusSM,
                display: 'flex',
                flexDirection: 'column',
                gap: 8,
              }}
            >
              <Text strong style={{ fontSize: 12 }}>3-way match summary</Text>
              <KvLine label="Purchase order" value={active.poNumber ?? 'No PO referenced'} />
              <KvLine label="Goods received note" value={active.poNumber ? `GRN-${active.poNumber.replace('PO-', '')}` : '—'} />
              <KvLine label="Vendor bill" value={active.billNumber} />
              <KvLine label="Bill date" value={formatDateShort(active.billDate)} />
              <KvLine label="Due date" value={formatDateShort(active.dueDate)} />
            </div>

            <div>
              <Text strong style={{ fontSize: 12 }}>Lines</Text>
              <div
                style={{
                  marginTop: 8,
                  padding: 14,
                  border: `1px dashed ${token.colorBorderSecondary}`,
                  borderRadius: token.borderRadiusSM,
                  fontSize: 12,
                  color: token.colorTextSecondary,
                }}
              >
                Line-level GL coding, tax breakup, and attachment preview live
                here. The match panel highlights any quantity / price /
                tolerance variance against the PO and GRN.
              </div>
            </div>

            <Space>
              <Button>Reject</Button>
              <Button type="primary">Approve & queue payment</Button>
            </Space>
          </Space>
        ) : null}
      </Drawer>
    </Space>
  );
}

function KvLine({ label, value }: { label: string; value: React.ReactNode }) {
  return (
    <div style={{ display: 'flex', justifyContent: 'space-between', fontSize: 12 }}>
      <Text type="secondary" style={{ fontSize: 12 }}>{label}</Text>
      <span style={{ fontVariantNumeric: 'tabular-nums' }}>{value}</span>
    </div>
  );
}
