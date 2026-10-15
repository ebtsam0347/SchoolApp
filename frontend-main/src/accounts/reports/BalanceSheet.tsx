import { Col, Row, Space, Table, theme as antdTheme } from 'antd';
import type { ColumnsType } from 'antd/es/table';

import { MoneyCell, PageHeader, Section } from '@/components';

import { PreviewNotice } from '../PreviewNotice';
import { coa } from '../mock/data';

const { useToken } = antdTheme;

interface Line {
  key: string;
  label: string;
  amount: number;
  bold?: boolean;
  indent?: number;
}

function leafLines(typeName: string): Line[] {
  const top = coa.find((n) => n.name === typeName);
  if (!top) return [];
  const out: Line[] = [{ key: top.code, label: top.name, amount: top.balance, bold: true }];
  for (const child of top.children ?? []) {
    if (child.children?.length) {
      out.push({ key: child.code, label: child.name, amount: child.balance, bold: true, indent: 1 });
      for (const leaf of child.children) {
        out.push({ key: leaf.code, label: leaf.name, amount: leaf.balance, indent: 2 });
      }
    } else {
      out.push({ key: child.code, label: child.name, amount: child.balance, indent: 1 });
    }
  }
  return out;
}

export function BalanceSheet() {
  const { token } = useToken();
  const assets = leafLines('Assets');
  const liabilities = leafLines('Liabilities');
  const equity = leafLines('Equity');

  const columns: ColumnsType<Line> = [
    {
      title: 'Line',
      dataIndex: 'label',
      key: 'label',
      render: (v: string, row) => (
        <span
          style={{
            fontWeight: row.bold ? 700 : 400,
            paddingLeft: (row.indent ?? 0) * 16,
            color: row.bold ? token.colorText : token.colorTextSecondary,
          }}
        >
          {v}
        </span>
      ),
    },
    {
      title: 'Amount',
      dataIndex: 'amount',
      key: 'amount',
      width: 220,
      align: 'right',
      render: (v: number, row) => (
        <span style={{ fontWeight: row.bold ? 700 : 500 }}>
          <MoneyCell amount={v} compact />
        </span>
      ),
    },
  ];

  return (
    <Space orientation="vertical" size={20} style={{ width: '100%' }}>
      <PageHeader eyebrow="REPORTS" title="Balance sheet" description="Snapshot of financial position as of 30 Apr 2026." />
      <PreviewNotice />
      <Row gutter={[16, 16]}>
        <Col xs={24} lg={12}>
          <Section title="Assets">
            <Table<Line> rowKey="key" columns={columns} dataSource={assets} pagination={false} size="middle" />
          </Section>
        </Col>
        <Col xs={24} lg={12}>
          <Space orientation="vertical" size={16} style={{ width: '100%' }}>
            <Section title="Liabilities">
              <Table<Line> rowKey="key" columns={columns} dataSource={liabilities} pagination={false} size="middle" />
            </Section>
            <Section title="Equity">
              <Table<Line> rowKey="key" columns={columns} dataSource={equity} pagination={false} size="middle" />
            </Section>
          </Space>
        </Col>
      </Row>
    </Space>
  );
}
