import { useMemo, useState } from 'react';
import { Button, Input, Space, Table, Tag, Typography, theme as antdTheme } from 'antd';
import type { ColumnsType } from 'antd/es/table';
import { Plus, Search } from 'lucide-react';

import { MoneyCell, PageHeader, Section } from '@/components';

import { PreviewNotice } from '../PreviewNotice';
import { coa, type CoaNode } from '../mock/data';

const { Text } = Typography;
const { useToken } = antdTheme;

const typeColors: Record<CoaNode['type'], string> = {
  Asset: 'blue',
  Liability: 'red',
  Equity: 'purple',
  Income: 'green',
  Expense: 'orange',
};

export function ChartOfAccounts() {
  const { token } = useToken();
  const [search, setSearch] = useState('');

  const data = useMemo(() => filterCoa(coa, search.trim().toLowerCase()), [search]);

  const columns: ColumnsType<CoaNode> = [
    {
      title: 'Code',
      dataIndex: 'code',
      key: 'code',
      width: 120,
      render: (c: string) => (
        <Text style={{ fontFamily: 'var(--font-mono, ui-monospace)', fontSize: 13 }}>{c}</Text>
      ),
    },
    {
      title: 'Account',
      dataIndex: 'name',
      key: 'name',
      render: (n: string, row) =>
        row.children ? <Text strong>{n}</Text> : <Text>{n}</Text>,
    },
    {
      title: 'Type',
      dataIndex: 'type',
      key: 'type',
      width: 130,
      render: (t: CoaNode['type']) => <Tag color={typeColors[t]}>{t}</Tag>,
    },
    {
      title: 'Balance',
      dataIndex: 'balance',
      key: 'balance',
      width: 200,
      align: 'right',
      render: (v: number, row) => (
        <span style={{ fontWeight: row.children ? 700 : 500 }}>
          <MoneyCell amount={v} compact />
        </span>
      ),
    },
  ];

  return (
    <Space orientation="vertical" size={20} style={{ width: '100%' }}>
      <PageHeader
        eyebrow="LEDGER"
        title="Chart of accounts"
        description="The structure of every account in the GL. Tree-organised by type, with running balances rolled up."
        actions={
          <Button type="primary" icon={<Plus size={14} strokeWidth={1.75} />}>
            Add account
          </Button>
        }
      />

      <PreviewNotice />

      <Section title="Accounts tree" subtitle="Search by code or name; expand to drill into a branch">
        <div style={{ marginBottom: 12, display: 'flex', alignItems: 'center', gap: 8, flexWrap: 'wrap' }}>
          <Input
            prefix={<Search size={14} strokeWidth={1.75} />}
            placeholder="Search code or name…"
            value={search}
            onChange={(e) => setSearch(e.target.value)}
            allowClear
            style={{ minWidth: 280, maxWidth: 360 }}
          />
          <div style={{ display: 'flex', gap: 6, fontSize: 11 }}>
            {(['Asset', 'Liability', 'Equity', 'Income', 'Expense'] as const).map((t) => (
              <Tag key={t} color={typeColors[t]}>
                {t}
              </Tag>
            ))}
          </div>
        </div>
        <Table<CoaNode>
          rowKey="code"
          columns={columns}
          dataSource={data}
          pagination={false}
          size="middle"
          defaultExpandAllRows={search.length > 0}
          expandable={{
            defaultExpandAllRows: false,
            indentSize: 24,
          }}
          style={{
            background: token.colorBgContainer,
            borderRadius: token.borderRadiusLG,
          }}
        />
      </Section>
    </Space>
  );
}

function filterCoa(nodes: CoaNode[], q: string): CoaNode[] {
  if (!q) return nodes;
  const out: CoaNode[] = [];
  for (const n of nodes) {
    const matches =
      n.code.toLowerCase().includes(q) || n.name.toLowerCase().includes(q);
    if (matches) {
      out.push(n);
      continue;
    }
    if (n.children) {
      const kids = filterCoa(n.children, q);
      if (kids.length > 0) out.push({ ...n, children: kids });
    }
  }
  return out;
}
