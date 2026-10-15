import { Card, Col, Row, Space, Typography } from 'antd';
import { Link } from '@tanstack/react-router';
import { BarChart3, FileBarChart, FilePieChart, LineChart, TrendingUp } from 'lucide-react';

import { PageHeader, Section } from '@/components';

import { PreviewNotice } from '../PreviewNotice';

const { Text, Title } = Typography;

interface ReportTile {
  to: string;
  title: string;
  description: string;
  icon: typeof BarChart3;
}

const tiles: ReportTile[] = [
  { to: '/accounts/reports/profit-loss', title: 'Profit & loss', description: 'YTD P&L vs prior year comparatives.', icon: LineChart },
  { to: '/accounts/reports/balance-sheet', title: 'Balance sheet', description: 'Period-end snapshot of assets, liabilities, and equity.', icon: BarChart3 },
  { to: '/accounts/reports/cash-flow', title: 'Cash flow', description: 'Indirect-method cash flow with monthly net trend.', icon: TrendingUp },
];

export function ReportsLanding() {
  return (
    <Space orientation="vertical" size={20} style={{ width: '100%' }}>
      <PageHeader
        eyebrow="REPORTS"
        title="Custom reports"
        description="Headline statements plus a builder for ad-hoc breakdowns by department, cost centre, customer, or product."
      />
      <PreviewNotice />

      <Section title="Standard statements">
        <Row gutter={[16, 16]}>
          {tiles.map((t) => {
            const Icon = t.icon;
            return (
              <Col xs={24} md={12} lg={8} key={t.to}>
                <Link to={t.to} style={{ textDecoration: 'none' }}>
                  <Card
                    hoverable
                    styles={{ body: { padding: 18, display: 'flex', flexDirection: 'column', gap: 8 } }}
                  >
                    <Icon size={20} strokeWidth={1.75} />
                    <Title level={5} style={{ margin: 0 }}>{t.title}</Title>
                    <Text type="secondary" style={{ fontSize: 12 }}>{t.description}</Text>
                  </Card>
                </Link>
              </Col>
            );
          })}
        </Row>
      </Section>

      <Section title="Custom report builder">
        <Card styles={{ body: { padding: 24, textAlign: 'center' } }}>
          <FileBarChart size={32} strokeWidth={1.5} style={{ opacity: 0.5 }} />
          <Title level={5} style={{ margin: '12px 0 4px' }}>Build any report</Title>
          <Text type="secondary">
            Pick measures (revenue, COGS, payroll, tax) and dimensions (period,
            department, cost centre, customer, product) to produce ad-hoc views
            without leaving the workspace.
          </Text>
          <div style={{ marginTop: 12 }}>
            <FilePieChart size={14} strokeWidth={1.75} style={{ verticalAlign: 'middle', marginRight: 6 }} />
            <Text type="secondary" style={{ fontSize: 12 }}>Coming next iteration.</Text>
          </div>
        </Card>
      </Section>
    </Space>
  );
}
