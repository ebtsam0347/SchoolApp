import { Space, Table, Typography, theme as antdTheme } from 'antd';
import type { ColumnsType } from 'antd/es/table';

import { KpiGrid, MoneyCell, PageHeader, Section, StatCard } from '@/components';
import { Package } from 'lucide-react';

import { PreviewNotice } from '../PreviewNotice';
import { mfgKpis } from '../mock/data';

const { Text } = Typography;
const { useToken } = antdTheme;

interface InventoryRow {
  category: string;
  units: number;
  unitValue: number;
  totalValue: number;
  turnover: number;
}

const rows: InventoryRow[] = [
  { category: 'Raw — leather hides', units: 18_400, unitValue: 4_280, totalValue: 78_752_000, turnover: 5.2 },
  { category: 'Raw — synthetic fabric', units: 26_800, unitValue: 1_980, totalValue: 53_064_000, turnover: 4.6 },
  { category: 'Raw — yarn', units: 92_400, unitValue: 612, totalValue: 56_548_800, turnover: 6.1 },
  { category: 'Raw — adhesives', units: 1_280, unitValue: 18_400, totalValue: 23_552_000, turnover: 3.8 },
  { category: 'WIP — football panels', units: 64_200, unitValue: 410, totalValue: 26_322_000, turnover: 12.4 },
  { category: 'WIP — assembled balls', units: 38_400, unitValue: 1_120, totalValue: 43_008_000, turnover: 9.2 },
  { category: 'Finished — match balls', units: 84_200, unitValue: 1_840, totalValue: 154_928_000, turnover: 4.4 },
  { category: 'Finished — training balls', units: 28_800, unitValue: 1_290, totalValue: 37_152_000, turnover: 5.1 },
];

export function Inventory() {
  const { token } = useToken();
  const total = rows.reduce((s, r) => s + r.totalValue, 0);

  const columns: ColumnsType<InventoryRow> = [
    { title: 'Category', dataIndex: 'category', key: 'category', render: (v: string) => <Text strong>{v}</Text> },
    {
      title: 'Units',
      dataIndex: 'units',
      key: 'units',
      width: 120,
      align: 'right',
      render: (v: number) => v.toLocaleString('en-PK'),
    },
    {
      title: 'Unit value',
      dataIndex: 'unitValue',
      key: 'unitValue',
      width: 140,
      align: 'right',
      render: (v: number) => <MoneyCell amount={v} compact />,
    },
    {
      title: 'Total value',
      dataIndex: 'totalValue',
      key: 'totalValue',
      width: 180,
      align: 'right',
      render: (v: number) => <MoneyCell amount={v} compact />,
    },
    {
      title: 'Turnover (×)',
      dataIndex: 'turnover',
      key: 'turnover',
      width: 130,
      align: 'right',
      render: (v: number) => `${v.toFixed(1)}×`,
    },
  ];

  return (
    <Space orientation="vertical" size={20} style={{ width: '100%' }}>
      <PageHeader
        eyebrow="MANUFACTURING"
        title="Inventory & WIP"
        description="Valuation of raw material, work-in-progress, and finished goods — with category-level turnover."
      />
      <PreviewNotice />

      <KpiGrid minColumnWidth={220}>
        <StatCard
          variant="accent-radial"
          tone="primary"
          icon={Package}
          label="Total inventory"
          value={<MoneyCell amount={total} compact />}
          accentCorner="tr"
        />
        <StatCard
          variant="accent-radial"
          tone="info"
          icon={Package}
          label="WIP value"
          value={<MoneyCell amount={mfgKpis.wipValue} compact />}
          accentCorner="tr"
        />
        <StatCard
          variant="accent-radial"
          tone="success"
          icon={Package}
          label="Turnover"
          value={`${mfgKpis.inventoryTurnover.toFixed(1)}×`}
          delta="annualised"
          accentCorner="tr"
        />
        <StatCard
          variant="accent-radial"
          tone="warning"
          icon={Package}
          label="Scrap rate"
          value={`${mfgKpis.scrapPct.toFixed(1)}%`}
          accentCorner="tr"
        />
      </KpiGrid>

      <Section title="By category">
        <Table<InventoryRow>
          rowKey="category"
          columns={columns}
          dataSource={rows}
          pagination={false}
          size="middle"
          summary={() => (
            <Table.Summary fixed="bottom">
              <Table.Summary.Row style={{ background: token.colorFillTertiary }}>
                <Table.Summary.Cell index={0}>
                  <Text strong>Total</Text>
                </Table.Summary.Cell>
                <Table.Summary.Cell index={1} align="right">
                  <Text strong>{rows.reduce((s, r) => s + r.units, 0).toLocaleString('en-PK')}</Text>
                </Table.Summary.Cell>
                <Table.Summary.Cell index={2} />
                <Table.Summary.Cell index={3} align="right">
                  <Text strong>
                    <MoneyCell amount={total} compact />
                  </Text>
                </Table.Summary.Cell>
                <Table.Summary.Cell index={4} />
              </Table.Summary.Row>
            </Table.Summary>
          )}
        />
      </Section>
    </Space>
  );
}
