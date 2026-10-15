import { Button, Space, Table, Typography, theme as antdTheme } from 'antd';
import type { ColumnsType } from 'antd/es/table';
import { Download, FileText } from 'lucide-react';

import { MoneyCell, PageHeader, Section } from '@/components';

import { PreviewNotice } from '../PreviewNotice';
import { execKpis } from '../mock/data';

const { Text } = Typography;
const { useToken } = antdTheme;

interface Line {
  key: string;
  label: string;
  ytd: number;
  prior: number;
  bold?: boolean;
  indent?: number;
  divider?: boolean;
}

const cogs = Math.round(execKpis.ytdRevenue * 0.616);
const grossProfit = execKpis.ytdRevenue - cogs;
const opex = Math.round(execKpis.ytdRevenue * 0.21);
const operatingIncome = grossProfit - opex;
const tax = Math.round(operatingIncome * 0.29);
const netIncome = operatingIncome - tax;

const lines: Line[] = [
  { key: 'rev', label: 'Revenue', ytd: execKpis.ytdRevenue, prior: 2_786_400_000, bold: true },
  { key: 'rev-export', label: 'Sales — exports', ytd: 2_186_400_000, prior: 1_948_200_000, indent: 1 },
  { key: 'rev-local', label: 'Sales — local', ytd: 924_120_000, prior: 802_400_000, indent: 1 },
  { key: 'rev-other', label: 'Other income', ytd: 37_700_000, prior: 35_800_000, indent: 1 },
  { key: 'cogs', label: 'Cost of goods sold', ytd: -cogs, prior: -1_720_000_000, divider: true },
  { key: 'gp', label: 'Gross profit', ytd: grossProfit, prior: 1_066_400_000, bold: true },
  { key: 'opex-h', label: 'Operating expenses', ytd: -opex, prior: -566_900_000, bold: true, divider: true },
  { key: 'opex-sal', label: 'Salaries & wages', ytd: -421_900_000, prior: -388_400_000, indent: 1 },
  { key: 'opex-util', label: 'Utilities', ytd: -84_200_000, prior: -76_400_000, indent: 1 },
  { key: 'opex-frt', label: 'Freight & duty', ytd: -142_400_000, prior: -118_900_000, indent: 1 },
  { key: 'opex-dep', label: 'Depreciation', ytd: -56_400_000, prior: -52_800_000, indent: 1 },
  { key: 'opex-other', label: 'Other operating expenses', ytd: -46_980_000, prior: -38_900_000, indent: 1 },
  { key: 'op-inc', label: 'Operating income', ytd: operatingIncome, prior: 499_500_000, bold: true, divider: true },
  { key: 'tax', label: 'Income tax', ytd: -tax, prior: -141_900_000 },
  { key: 'ni', label: 'Net income', ytd: netIncome, prior: 357_600_000, bold: true, divider: true },
];

export function ProfitLoss() {
  const { token } = useToken();
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
      title: 'YTD',
      dataIndex: 'ytd',
      key: 'ytd',
      width: 200,
      align: 'right',
      render: (v: number, row) => (
        <span style={{ fontWeight: row.bold ? 700 : 500 }}>
          <MoneyCell amount={v} compact coloredSign={row.bold} />
        </span>
      ),
    },
    {
      title: 'Prior YTD',
      dataIndex: 'prior',
      key: 'prior',
      width: 200,
      align: 'right',
      render: (v: number) => (
        <Text type="secondary">
          <MoneyCell amount={v} compact />
        </Text>
      ),
    },
    {
      title: 'Δ %',
      key: 'delta',
      width: 120,
      align: 'right',
      render: (_, row) => {
        if (row.prior === 0) return '—';
        const pct = ((row.ytd - row.prior) / Math.abs(row.prior)) * 100;
        const positive = pct > 0;
        const color = row.bold ? (positive ? token.colorSuccess : token.colorError) : undefined;
        return <span style={{ color, fontWeight: row.bold ? 700 : 400 }}>{positive ? '+' : ''}{pct.toFixed(1)}%</span>;
      },
    },
  ];

  return (
    <Space orientation="vertical" size={20} style={{ width: '100%' }}>
      <PageHeader
        eyebrow="REPORTS"
        title="Profit & loss"
        description="Year-to-date profit and loss with prior-year comparatives."
        actions={
          <Space>
            <Button icon={<Download size={14} strokeWidth={1.75} />}>CSV</Button>
            <Button icon={<FileText size={14} strokeWidth={1.75} />}>PDF</Button>
          </Space>
        }
      />
      <PreviewNotice />
      <Section title="As of 30 Apr 2026" subtitle="YTD vs same period last year">
        <Table<Line>
          rowKey="key"
          columns={columns}
          dataSource={lines}
          pagination={false}
          size="middle"
          rowClassName={(row) => (row.divider ? 'pl-divider' : '')}
        />
      </Section>
    </Space>
  );
}
