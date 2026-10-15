import { Button, Space, Table, Tag, Typography, theme as antdTheme } from 'antd';
import type { ColumnsType } from 'antd/es/table';
import { Download, FileText } from 'lucide-react';

import { MoneyCell, PageHeader, Section } from '@/components';

import { PreviewNotice } from '../PreviewNotice';
import { coa, type CoaNode } from '../mock/data';

const { Text } = Typography;
const { useToken } = antdTheme;

interface TrialRow {
  code: string;
  name: string;
  type: CoaNode['type'];
  debit: number;
  credit: number;
}

function flattenLeaves(nodes: CoaNode[]): TrialRow[] {
  const out: TrialRow[] = [];
  const walk = (list: CoaNode[]) => {
    for (const n of list) {
      if (n.children?.length) walk(n.children);
      else {
        const isDebitNormal = n.type === 'Asset' || n.type === 'Expense';
        out.push({
          code: n.code,
          name: n.name,
          type: n.type,
          debit: isDebitNormal ? n.balance : 0,
          credit: isDebitNormal ? 0 : n.balance,
        });
      }
    }
  };
  walk(nodes);
  return out.sort((a, b) => a.code.localeCompare(b.code));
}

export function TrialBalance() {
  const { token } = useToken();
  const rows = flattenLeaves(coa);
  const totalDebit = rows.reduce((s, r) => s + r.debit, 0);
  const totalCredit = rows.reduce((s, r) => s + r.credit, 0);
  const balanced = Math.abs(totalDebit - totalCredit) < 1;

  const columns: ColumnsType<TrialRow> = [
    {
      title: 'Code',
      dataIndex: 'code',
      key: 'code',
      width: 110,
      render: (c: string) => (
        <Text style={{ fontFamily: 'var(--font-mono, ui-monospace)', fontSize: 13 }}>{c}</Text>
      ),
    },
    { title: 'Account', dataIndex: 'name', key: 'name' },
    {
      title: 'Type',
      dataIndex: 'type',
      key: 'type',
      width: 110,
      render: (t: CoaNode['type']) => (
        <Text type="secondary" style={{ fontSize: 12 }}>
          {t}
        </Text>
      ),
    },
    {
      title: 'Debit',
      dataIndex: 'debit',
      key: 'debit',
      width: 160,
      align: 'right',
      render: (v: number) => (v ? <MoneyCell amount={v} compact /> : <span style={{ color: token.colorTextQuaternary }}>—</span>),
    },
    {
      title: 'Credit',
      dataIndex: 'credit',
      key: 'credit',
      width: 160,
      align: 'right',
      render: (v: number) => (v ? <MoneyCell amount={v} compact /> : <span style={{ color: token.colorTextQuaternary }}>—</span>),
    },
  ];

  return (
    <Space orientation="vertical" size={20} style={{ width: '100%' }}>
      <PageHeader
        eyebrow="LEDGER"
        title="Trial balance"
        description="Period-end aggregation of every GL account, with debits and credits grouped — the snapshot review goes through before close."
        actions={
          <Space>
            <Button icon={<Download size={14} strokeWidth={1.75} />}>Export CSV</Button>
            <Button icon={<FileText size={14} strokeWidth={1.75} />}>PDF</Button>
          </Space>
        }
      />

      <PreviewNotice />

      <Section
        title="As of 30 Apr 2026"
        actions={
          <Tag color={balanced ? 'green' : 'red'} style={{ fontSize: 12 }}>
            {balanced ? 'Balanced — Dr = Cr' : 'Out of balance'}
          </Tag>
        }
      >
        <Table<TrialRow>
          rowKey="code"
          columns={columns}
          dataSource={rows}
          pagination={false}
          size="middle"
          summary={() => (
            <Table.Summary fixed="bottom">
              <Table.Summary.Row style={{ background: token.colorFillTertiary }}>
                <Table.Summary.Cell index={0} colSpan={3}>
                  <Text strong>Total</Text>
                </Table.Summary.Cell>
                <Table.Summary.Cell index={3} align="right">
                  <Text strong>
                    <MoneyCell amount={totalDebit} compact />
                  </Text>
                </Table.Summary.Cell>
                <Table.Summary.Cell index={4} align="right">
                  <Text strong>
                    <MoneyCell amount={totalCredit} compact />
                  </Text>
                </Table.Summary.Cell>
              </Table.Summary.Row>
            </Table.Summary>
          )}
        />
      </Section>
    </Space>
  );
}
