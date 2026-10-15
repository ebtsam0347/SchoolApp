import { Space } from 'antd';

import { ChartCard, PageHeader, Section, StackedBars, TrendLine } from '@/components';
import { CURRENCY_SYMBOL } from '@/config/locale';

import { PreviewNotice } from '../PreviewNotice';
import { cashForecast, monthly } from '../mock/data';

const compactMoney = (n: number) =>
  new Intl.NumberFormat('en-PK', { notation: 'compact', maximumFractionDigits: 1 }).format(n);
const labelMoney = (n: number) => `${CURRENCY_SYMBOL} ${compactMoney(n)}`;

export function CashPosition() {
  const cashTrendData = monthly.map((m) => ({ month: m.month, cash: m.cashClose }));
  const forecastData = cashForecast.map((f) => ({
    week: new Date(f.weekStart).toLocaleDateString('en-PK', { month: 'short', day: 'numeric' }),
    inflows: f.inflows,
    outflows: -f.outflows,
    closing: f.closingBalance,
  }));

  return (
    <Space orientation="vertical" size={20} style={{ width: '100%' }}>
      <PageHeader
        eyebrow="BANKING"
        title="Cash position"
        description="Current cash, the 12-month closing trend, and a 13-week rolling forecast of inflows and outflows."
      />
      <PreviewNotice />

      <Section title="Closing cash trend" subtitle="12 months">
        <ChartCard height={280}>
          <TrendLine
            data={cashTrendData}
            xKey="month"
            series={[{ key: 'cash', label: 'Cash close', tone: 'primary' }]}
            yTickFormatter={(v) => compactMoney(v)}
            valueFormatter={(v) => labelMoney(v)}
          />
        </ChartCard>
      </Section>

      <Section title="13-week forecast" subtitle="Projected inflows and outflows">
        <ChartCard height={300}>
          <StackedBars
            data={forecastData}
            xKey="week"
            stacked={false}
            series={[
              { key: 'inflows', label: 'Inflows', tone: 'success' },
              { key: 'outflows', label: 'Outflows', tone: 'error' },
            ]}
            yTickFormatter={(v) => compactMoney(v)}
            valueFormatter={(v) => labelMoney(Math.abs(v))}
          />
        </ChartCard>
      </Section>
    </Space>
  );
}
