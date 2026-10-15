import { Space, Table, Tag, Typography } from 'antd';
import type { ColumnsType } from 'antd/es/table';

import { KpiGrid, MoneyCell, PageHeader, Section, StatCard } from '@/components';
import { CalendarCheck, Percent, Receipt } from 'lucide-react';

import { PreviewNotice } from '../PreviewNotice';

const { Text } = Typography;

interface MonthRow {
  month: string;
  outputTax: number;
  inputTax: number;
  payable: number;
  status: 'FILED' | 'OPEN' | 'DUE';
}

const rows: MonthRow[] = [
  { month: 'May 2026', outputTax: 53_120_000, inputTax: 32_080_000, payable: 21_040_000, status: 'OPEN' },
  { month: 'Apr 2026', outputTax: 50_823_000, inputTax: 30_920_000, payable: 19_903_000, status: 'DUE' },
  { month: 'Mar 2026', outputTax: 50_462_000, inputTax: 30_620_000, payable: 19_842_000, status: 'FILED' },
  { month: 'Feb 2026', outputTax: 46_618_000, inputTax: 28_780_000, payable: 17_838_000, status: 'FILED' },
  { month: 'Jan 2026', outputTax: 47_838_000, inputTax: 29_430_000, payable: 18_408_000, status: 'FILED' },
];

const tone: Record<MonthRow['status'], string> = {
  FILED: 'green',
  OPEN: 'gold',
  DUE: 'red',
};

export function SalesTax() {
  const columns: ColumnsType<MonthRow> = [
    { title: 'Period', dataIndex: 'month', key: 'month', render: (v) => <Text strong>{v}</Text> },
    { title: 'Output tax', dataIndex: 'outputTax', key: 'outputTax', width: 180, align: 'right', render: (v: number) => <MoneyCell amount={v} compact /> },
    { title: 'Input tax', dataIndex: 'inputTax', key: 'inputTax', width: 180, align: 'right', render: (v: number) => <MoneyCell amount={v} compact /> },
    { title: 'Payable', dataIndex: 'payable', key: 'payable', width: 180, align: 'right', render: (v: number) => <MoneyCell amount={v} compact /> },
    { title: 'Status', dataIndex: 'status', key: 'status', width: 120, render: (s: MonthRow['status']) => <Tag color={tone[s]}>{s}</Tag> },
  ];

  return (
    <Space orientation="vertical" size={20} style={{ width: '100%' }}>
      <PageHeader
        eyebrow="COMPLIANCE"
        title="Sales tax (FBR)"
        description="Monthly sales-tax register, input/output reconciliation, and filing status against FBR portal deadlines."
      />
      <PreviewNotice />

      <KpiGrid minColumnWidth={220}>
        <StatCard
          variant="accent-radial"
          tone="warning"
          icon={CalendarCheck}
          label="Next filing due"
          value="18 May 2026"
          delta="April annexure C"
          accentCorner="tr"
        />
        <StatCard
          variant="accent-radial"
          tone="primary"
          icon={Percent}
          label="Effective rate"
          value="17.0%"
          accentCorner="tr"
        />
        <StatCard
          variant="accent-radial"
          tone="info"
          icon={Receipt}
          label="May payable (so far)"
          value={<MoneyCell amount={21_040_000} compact />}
          accentCorner="tr"
        />
      </KpiGrid>

      <Section title="Monthly register">
        <Table<MonthRow> rowKey="month" columns={columns} dataSource={rows} pagination={false} size="middle" />
      </Section>
    </Space>
  );
}
