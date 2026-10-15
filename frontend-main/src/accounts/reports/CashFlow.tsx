import { Space, Table, theme as antdTheme } from 'antd';
import type { ColumnsType } from 'antd/es/table';

import { ChartCard, MoneyCell, PageHeader, Section, TrendLine } from '@/components';
import { CURRENCY_SYMBOL } from '@/config/locale';

import { PreviewNotice } from '../PreviewNotice';
import { monthly } from '../mock/data';

const { useToken } = antdTheme;

const compactMoney = (n: number) =>
  new Intl.NumberFormat('en-PK', { notation: 'compact', maximumFractionDigits: 1 }).format(n);
const labelMoney = (n: number) => `${CURRENCY_SYMBOL} ${compactMoney(n)}`;

interface CashLine {
  key: string;
  label: string;
  amount: number;
  bold?: boolean;
  indent?: number;
}

const lines: CashLine[] = [
  { key: 'op-h', label: 'Operating activities', amount: 482_900_000, bold: true },
  { key: 'op-ni', label: 'Net income', amount: 357_600_000, indent: 1 },
  { key: 'op-dep', label: 'Depreciation', amount: 56_400_000, indent: 1 },
  { key: 'op-wc', label: 'Working capital changes', amount: 68_900_000, indent: 1 },
  { key: 'inv-h', label: 'Investing activities', amount: -84_200_000, bold: true },
  { key: 'inv-cap', label: 'Capital expenditure', amount: -78_400_000, indent: 1 },
  { key: 'inv-other', label: 'Other investing', amount: -5_800_000, indent: 1 },
  { key: 'fin-h', label: 'Financing activities', amount: -42_800_000, bold: true },
  { key: 'fin-loan', label: 'Loan principal repaid', amount: -38_400_000, indent: 1 },
  { key: 'fin-int', label: 'Interest paid', amount: -4_400_000, indent: 1 },
  { key: 'net', label: 'Net change in cash', amount: 355_900_000, bold: true },
];

export function CashFlow() {
  const { token } = useToken();

  const columns: ColumnsType<CashLine> = [
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
          <MoneyCell amount={v} compact coloredSign={row.bold} />
        </span>
      ),
    },
  ];

  const monthlyNet = monthly.map((m) => ({
    month: m.month,
    net: m.cashIn - m.cashOut,
  }));

  return (
    <Space orientation="vertical" size={20} style={{ width: '100%' }}>
      <PageHeader
        eyebrow="REPORTS"
        title="Cash flow"
        description="Indirect-method cash flow statement YTD with monthly net cash trend."
      />
      <PreviewNotice />

      <Section title="Monthly net cash" subtitle="Inflows minus outflows, last 12 months">
        <ChartCard height={260}>
          <TrendLine
            data={monthlyNet}
            xKey="month"
            series={[{ key: 'net', label: 'Net cash', tone: 'success' }]}
            yTickFormatter={(v) => compactMoney(v)}
            valueFormatter={(v) => labelMoney(v)}
          />
        </ChartCard>
      </Section>

      <Section title="YTD cash flow statement">
        <Table<CashLine> rowKey="key" columns={columns} dataSource={lines} pagination={false} size="middle" />
      </Section>
    </Space>
  );
}
