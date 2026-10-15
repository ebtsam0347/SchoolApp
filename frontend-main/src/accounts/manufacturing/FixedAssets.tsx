import { Space, Table, Typography } from 'antd';
import type { ColumnsType } from 'antd/es/table';

import { MoneyCell, PageHeader, Section } from '@/components';

import { PreviewNotice } from '../PreviewNotice';

const { Text } = Typography;

interface AssetRow {
  category: string;
  cost: number;
  accumulated: number;
  netBookValue: number;
  monthlyDepr: number;
}

const rows: AssetRow[] = [
  { category: 'Plant & machinery', cost: 642_400_000, accumulated: 120_000_000, netBookValue: 522_400_000, monthlyDepr: 3_240_000 },
  { category: 'Buildings & civil', cost: 218_400_000, accumulated: 75_500_000, netBookValue: 142_900_000, monthlyDepr: 720_000 },
  { category: 'Office equipment', cost: 32_840_000, accumulated: 14_440_000, netBookValue: 18_400_000, monthlyDepr: 220_000 },
  { category: 'Vehicles', cost: 28_400_000, accumulated: 11_920_000, netBookValue: 16_480_000, monthlyDepr: 60_000 },
];

export function FixedAssets() {
  const total = rows.reduce(
    (acc, r) => ({
      cost: acc.cost + r.cost,
      accumulated: acc.accumulated + r.accumulated,
      netBookValue: acc.netBookValue + r.netBookValue,
      monthlyDepr: acc.monthlyDepr + r.monthlyDepr,
    }),
    { cost: 0, accumulated: 0, netBookValue: 0, monthlyDepr: 0 },
  );

  const columns: ColumnsType<AssetRow> = [
    { title: 'Category', dataIndex: 'category', key: 'category', render: (v) => <Text strong>{v}</Text> },
    { title: 'Cost', dataIndex: 'cost', key: 'cost', width: 200, align: 'right', render: (v: number) => <MoneyCell amount={v} compact /> },
    { title: 'Accumulated', dataIndex: 'accumulated', key: 'accumulated', width: 200, align: 'right', render: (v: number) => <MoneyCell amount={v} compact /> },
    { title: 'NBV', dataIndex: 'netBookValue', key: 'netBookValue', width: 200, align: 'right', render: (v: number) => <MoneyCell amount={v} compact /> },
    { title: 'Monthly depr', dataIndex: 'monthlyDepr', key: 'monthlyDepr', width: 200, align: 'right', render: (v: number) => <MoneyCell amount={v} compact /> },
  ];

  return (
    <Space orientation="vertical" size={20} style={{ width: '100%' }}>
      <PageHeader
        eyebrow="MANUFACTURING"
        title="Fixed assets"
        description="Asset register grouped by category — cost, accumulated depreciation, and current net book value."
      />
      <PreviewNotice />
      <Section title="Asset register">
        <Table<AssetRow>
          rowKey="category"
          columns={columns}
          dataSource={rows}
          pagination={false}
          size="middle"
          summary={() => (
            <Table.Summary fixed="bottom">
              <Table.Summary.Row>
                <Table.Summary.Cell index={0}><Text strong>Total</Text></Table.Summary.Cell>
                <Table.Summary.Cell index={1} align="right"><Text strong><MoneyCell amount={total.cost} compact /></Text></Table.Summary.Cell>
                <Table.Summary.Cell index={2} align="right"><Text strong><MoneyCell amount={total.accumulated} compact /></Text></Table.Summary.Cell>
                <Table.Summary.Cell index={3} align="right"><Text strong><MoneyCell amount={total.netBookValue} compact /></Text></Table.Summary.Cell>
                <Table.Summary.Cell index={4} align="right"><Text strong><MoneyCell amount={total.monthlyDepr} compact /></Text></Table.Summary.Cell>
              </Table.Summary.Row>
            </Table.Summary>
          )}
        />
      </Section>
    </Space>
  );
}
