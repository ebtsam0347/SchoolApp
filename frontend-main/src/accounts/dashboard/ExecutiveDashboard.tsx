// Executive finance dashboard — the headline of the Accounts (Preview)
// workspace. Layout: hero KPI strip → cash & liquidity row → P&L band →
// receivables / payables aging → manufacturing pulse → top customers /
// products. Token-only; reuses StatCard/ChartCard/MoneyCell.

import { Card, Col, Row, Space, Typography, theme as antdTheme } from 'antd';
import {
  AlertTriangle,
  Banknote,
  Coins,
  FileWarning,
  Gauge,
  Package,
  Percent,
  TrendingUp,
  Wallet,
} from 'lucide-react';

import {
  ChartCard,
  HorizontalBars,
  KpiGrid,
  MoneyCell,
  PageHeader,
  Section,
  StatCard,
  StackedBars,
  TrendLine,
} from '@/components';
import { CURRENCY_SYMBOL } from '@/config/locale';

import { PreviewNotice } from '../PreviewNotice';
import {
  apAging,
  arAging,
  cashForecast,
  execKpis,
  mfgKpis,
  monthly,
  topCustomers,
  topProducts,
} from '../mock/data';

const { Text } = Typography;
const { useToken } = antdTheme;

const compactMoney = (n: number) =>
  new Intl.NumberFormat('en-PK', {
    notation: 'compact',
    maximumFractionDigits: 1,
  }).format(n);

const labelMoney = (n: number) => `${CURRENCY_SYMBOL} ${compactMoney(n)}`;

export function ExecutiveDashboard() {
  const { token } = useToken();

  const cashForecastEnd = cashForecast[cashForecast.length - 1];

  const cashTrendData = monthly.map((m) => ({
    month: m.month,
    cash: m.cashClose,
    revenue: m.revenue,
  }));

  const plData = monthly.map((m) => ({
    month: m.month,
    cogs: m.cogs,
    opex: m.opex,
    net: m.netIncome,
  }));

  return (
    <Space orientation="vertical" size={24} style={{ width: '100%' }}>
      <PageHeader
        eyebrow="ACCOUNTS · MAY 2026"
        title="Financial command centre"
        description="Live posture of the business — cash, profitability, working capital, and the manufacturing economics that drive them."
      />

      <PreviewNotice />

      {/* Hero KPI strip — the four numbers leadership reads first */}
      <KpiGrid minColumnWidth={260}>
        <StatCard
          variant="hero"
          tone="primary"
          icon={Wallet}
          label="Cash on hand"
          value={<MoneyCell amount={execKpis.cashOnHand} compact />}
          delta={
            <Text style={{ color: 'rgba(255,255,255,0.85)', fontSize: 13 }}>
              +{execKpis.cashDeltaPct.toFixed(1)}% vs last close
            </Text>
          }
          trend="up"
        />
        <StatCard
          variant="accent-radial"
          tone="success"
          icon={TrendingUp}
          label="Revenue MTD"
          value={<MoneyCell amount={execKpis.mtdRevenue} compact />}
          delta={`+${execKpis.mtdRevenueDeltaPct.toFixed(1)}% vs prior MTD`}
          trend="up"
          accentCorner="tr"
        />
        <StatCard
          variant="accent-radial"
          tone="info"
          icon={Percent}
          label="Gross margin"
          value={`${execKpis.grossMarginPct.toFixed(1)}%`}
          delta={`Net ${execKpis.netMarginPct.toFixed(1)}%`}
          accentCorner="tr"
        />
        <StatCard
          variant="accent-radial"
          tone="warning"
          icon={Gauge}
          label="Working capital"
          value={<MoneyCell amount={execKpis.workingCapital} compact />}
          delta={`AR ${execKpis.arDays}d · AP ${execKpis.apDays}d`}
          accentCorner="tr"
        />
      </KpiGrid>

      {/* Cash & liquidity */}
      <Section
        title="Cash & liquidity"
        subtitle="Closing cash trend and the next 13 weeks of forecast inflows / outflows"
      >
        <Row gutter={[16, 16]}>
          <Col xs={24} lg={16}>
            <ChartCard
              title="Closing cash — last 12 months"
              subtitle="Month-end cash position vs revenue"
              height={280}
            >
              <TrendLine
                data={cashTrendData}
                xKey="month"
                series={[
                  { key: 'cash', label: 'Cash close', tone: 'primary' },
                  { key: 'revenue', label: 'Revenue', tone: 'info', dash: 'dashed' },
                ]}
                yTickFormatter={(v) => compactMoney(v)}
                valueFormatter={(v) => labelMoney(v)}
              />
            </ChartCard>
          </Col>
          <Col xs={24} lg={8}>
            <Card
              styles={{
                body: {
                  display: 'flex',
                  flexDirection: 'column',
                  gap: 14,
                  padding: 18,
                  height: '100%',
                  minHeight: 280,
                },
              }}
            >
              <Space size={8} align="center">
                <Coins size={16} strokeWidth={1.75} color={token.colorPrimary} />
                <Text strong>13-week cash forecast</Text>
              </Space>
              <Text type="secondary" style={{ fontSize: 12 }}>
                Projected to {new Date(cashForecastEnd.weekStart).toLocaleDateString('en-PK', { month: 'short', day: 'numeric' })}
              </Text>
              <div
                style={{
                  display: 'flex',
                  flexDirection: 'column',
                  gap: 6,
                  marginTop: 6,
                }}
              >
                <KvRow label="Projected close" value={<MoneyCell amount={cashForecastEnd.closingBalance} compact />} strong />
                <KvRow label="Avg weekly inflow" value={<MoneyCell amount={66_400_000} compact />} />
                <KvRow label="Avg weekly outflow" value={<MoneyCell amount={62_900_000} compact />} />
                <KvRow
                  label="Net weekly run-rate"
                  value={
                    <span style={{ color: token.colorSuccess, fontWeight: 600 }}>
                      <MoneyCell amount={3_500_000} compact showSign />
                    </span>
                  }
                />
              </div>
              <div
                style={{
                  marginTop: 'auto',
                  padding: 10,
                  background: token.colorFillTertiary,
                  borderRadius: token.borderRadiusSM,
                  display: 'flex',
                  gap: 8,
                  alignItems: 'flex-start',
                }}
              >
                <AlertTriangle
                  size={14}
                  strokeWidth={1.75}
                  color={token.colorWarning}
                  style={{ flexShrink: 0, marginTop: 2 }}
                />
                <Text style={{ fontSize: 12 }}>
                  Week of <strong>{new Date(cashForecast[6].weekStart).toLocaleDateString('en-PK', { month: 'short', day: 'numeric' })}</strong> — large vendor disbursement; review release schedule.
                </Text>
              </div>
            </Card>
          </Col>
        </Row>
      </Section>

      {/* P&L band */}
      <Section
        title="Profit & loss"
        subtitle="12-month revenue, COGS, OpEx and net income"
      >
        <ChartCard
          title="Revenue, COGS & OpEx"
          subtitle="Stacked monthly composition with net income overlay"
          height={300}
        >
          <StackedBars
            data={plData}
            xKey="month"
            stacked={false}
            series={[
              { key: 'cogs', label: 'COGS', tone: 'warning' },
              { key: 'opex', label: 'OpEx', tone: 'info' },
              { key: 'net', label: 'Net income', tone: 'success' },
            ]}
            yTickFormatter={(v) => compactMoney(v)}
            valueFormatter={(v) => labelMoney(v)}
          />
        </ChartCard>
      </Section>

      {/* Receivables & payables */}
      <Section
        title="Receivables & payables"
        subtitle="Aged exposure across customers and vendors"
      >
        <Row gutter={[16, 16]}>
          <Col xs={24} lg={12}>
            <ChartCard
              title="AR aging"
              subtitle={
                <span>
                  <MoneyCell amount={execKpis.arOutstanding} compact /> outstanding · {execKpis.arDays} days outstanding
                </span>
              }
              height={260}
            >
              <HorizontalBars
                rows={arAging.map((b) => ({
                  id: b.label,
                  label: b.label,
                  value: b.amount,
                  caption: <MoneyCell amount={b.amount} compact />,
                  tone: b.label === '90+' ? 'error' : b.label === '61–90' ? 'warning' : 'primary',
                }))}
                topN={Infinity}
                sort="none"
                valueFormatter={(v) => labelMoney(v)}
              />
            </ChartCard>
          </Col>
          <Col xs={24} lg={12}>
            <ChartCard
              title="AP aging"
              subtitle={
                <span>
                  <MoneyCell amount={execKpis.apOutstanding} compact /> outstanding · {execKpis.apDays} days outstanding
                </span>
              }
              height={260}
            >
              <HorizontalBars
                rows={apAging.map((b) => ({
                  id: b.label,
                  label: b.label,
                  value: b.amount,
                  caption: <MoneyCell amount={b.amount} compact />,
                  tone: b.label === '90+' ? 'error' : b.label === '61–90' ? 'warning' : 'info',
                }))}
                topN={Infinity}
                sort="none"
                valueFormatter={(v) => labelMoney(v)}
              />
            </ChartCard>
          </Col>
        </Row>
      </Section>

      {/* Manufacturing pulse */}
      <Section
        title="Manufacturing economics"
        subtitle="Cost per unit, inventory and production health"
      >
        <Row gutter={[16, 16]}>
          <Col xs={12} md={6}>
            <StatCard
              variant="accent-corner"
              tone="primary"
              icon={Banknote}
              label="Cost per unit"
              value={`${CURRENCY_SYMBOL} ${mfgKpis.costPerUnitTrend[mfgKpis.costPerUnitTrend.length - 1].pkr}`}
              delta="6-month low"
              trend="down"
            />
          </Col>
          <Col xs={12} md={6}>
            <StatCard
              variant="accent-corner"
              tone="info"
              icon={Package}
              label="Inventory turnover"
              value={`${mfgKpis.inventoryTurnover.toFixed(1)}×`}
              delta="annualised"
            />
          </Col>
          <Col xs={12} md={6}>
            <StatCard
              variant="accent-corner"
              tone="warning"
              icon={FileWarning}
              label="Scrap rate"
              value={`${mfgKpis.scrapPct.toFixed(1)}%`}
              delta="target ≤ 2.5%"
            />
          </Col>
          <Col xs={12} md={6}>
            <StatCard
              variant="accent-corner"
              tone="success"
              icon={TrendingUp}
              label="Units MTD"
              value={mfgKpis.productionUnitsMtd.toLocaleString('en-PK')}
              delta={`+${mfgKpis.productionUnitsMtdDeltaPct.toFixed(1)}%`}
              trend="up"
            />
          </Col>
          <Col xs={24}>
            <ChartCard
              title="Cost per unit — trailing 6 months"
              subtitle="Lower is better — driven by yarn, leather, and yield improvements"
              height={220}
            >
              <TrendLine
                data={mfgKpis.costPerUnitTrend}
                xKey="month"
                series={[{ key: 'pkr', label: 'PKR / unit', tone: 'primary' }]}
                yTickFormatter={(v) => `${CURRENCY_SYMBOL} ${v}`}
                valueFormatter={(v) => `${CURRENCY_SYMBOL} ${v}`}
              />
            </ChartCard>
          </Col>
        </Row>
      </Section>

      {/* Top customers / products */}
      <Section
        title="Where revenue is concentrated"
        subtitle="YTD breakdown — concentration risk and product mix"
      >
        <Row gutter={[16, 16]}>
          <Col xs={24} lg={12}>
            <ChartCard title="Top customers (YTD)" subtitle="Share of total revenue" height={300}>
              <HorizontalBars
                rows={topCustomers.map((c) => ({
                  id: c.name,
                  label: c.name,
                  value: c.amount,
                  caption: `${(c.share * 100).toFixed(1)}%`,
                  tone: 'primary',
                }))}
                topN={Infinity}
                valueFormatter={(v) => labelMoney(v)}
              />
            </ChartCard>
          </Col>
          <Col xs={24} lg={12}>
            <ChartCard title="Top products (YTD)" subtitle="Share of total revenue" height={300}>
              <HorizontalBars
                rows={topProducts.map((p) => ({
                  id: p.name,
                  label: p.name,
                  value: p.amount,
                  caption: `${(p.share * 100).toFixed(1)}%`,
                  tone: 'success',
                }))}
                topN={Infinity}
                valueFormatter={(v) => labelMoney(v)}
              />
            </ChartCard>
          </Col>
        </Row>
      </Section>
    </Space>
  );
}

function KvRow({
  label,
  value,
  strong,
}: {
  label: string;
  value: React.ReactNode;
  strong?: boolean;
}) {
  return (
    <div
      style={{
        display: 'flex',
        justifyContent: 'space-between',
        alignItems: 'baseline',
        fontSize: 13,
      }}
    >
      <Text type="secondary" style={{ fontSize: 12 }}>
        {label}
      </Text>
      <span style={{ fontWeight: strong ? 700 : 500, fontVariantNumeric: 'tabular-nums' }}>
        {value}
      </span>
    </div>
  );
}
