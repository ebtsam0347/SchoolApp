import { Space } from 'antd';

import { ChartCard, PageHeader, Section, StackedBars, TrendLine } from '@/components';
import { CURRENCY_SYMBOL } from '@/config/locale';

import { PreviewNotice } from '../PreviewNotice';
import { mfgKpis, monthly } from '../mock/data';

const compactMoney = (n: number) =>
  new Intl.NumberFormat('en-PK', { notation: 'compact', maximumFractionDigits: 1 }).format(n);
const labelMoney = (n: number) => `${CURRENCY_SYMBOL} ${compactMoney(n)}`;

export function Cogs() {
  const cogsBreakdown = monthly.slice(-6).map((m) => ({
    month: m.month,
    materials: Math.round(m.cogs * 0.62),
    labour: Math.round(m.cogs * 0.21),
    overhead: Math.round(m.cogs * 0.17),
  }));

  return (
    <Space orientation="vertical" size={20} style={{ width: '100%' }}>
      <PageHeader
        eyebrow="MANUFACTURING"
        title="Cost of production"
        description="Where each rupee of COGS is going — material, labour, and factory overhead — and how cost-per-unit is moving."
      />
      <PreviewNotice />

      <Section title="Cost-per-unit trend" subtitle="Lower is better — yarn, leather, and yield gains">
        <ChartCard height={240}>
          <TrendLine
            data={mfgKpis.costPerUnitTrend}
            xKey="month"
            series={[{ key: 'pkr', label: 'PKR / unit', tone: 'primary' }]}
            yTickFormatter={(v) => `${CURRENCY_SYMBOL} ${v}`}
            valueFormatter={(v) => `${CURRENCY_SYMBOL} ${v}`}
          />
        </ChartCard>
      </Section>

      <Section title="COGS composition" subtitle="Trailing 6 months — material / labour / overhead split">
        <ChartCard height={300}>
          <StackedBars
            data={cogsBreakdown}
            xKey="month"
            stacked
            series={[
              { key: 'materials', label: 'Materials', tone: 'primary' },
              { key: 'labour', label: 'Labour', tone: 'info' },
              { key: 'overhead', label: 'Overhead', tone: 'warning' },
            ]}
            yTickFormatter={(v) => compactMoney(v)}
            valueFormatter={(v) => labelMoney(v)}
          />
        </ChartCard>
      </Section>
    </Space>
  );
}
