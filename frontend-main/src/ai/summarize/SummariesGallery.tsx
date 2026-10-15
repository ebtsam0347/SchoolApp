// Gallery of three sample summaries — employee timeline, audit by actor,
// team pulse. Each card streams when you click "Generate", which is
// what the Summarize button on the live people / audit pages will do
// once it's wired through ai-service.

import { Card, Col, Row, Space, Typography } from 'antd';
import { History, ScrollText, Users } from 'lucide-react';

import { KpiGrid, PageHeader, Section, StatCard } from '@/components';

import { PreviewNotice } from '../PreviewNotice';
import { summaryFixtures } from '../mock/data';
import { SummaryCard } from './SummaryCard';

const { Text } = Typography;

export function SummariesGallery() {
  return (
    <Space orientation="vertical" size={24} style={{ width: '100%' }}>
      <PageHeader
        eyebrow="ASSISTANT · SUMMARIES"
        title="Summaries gallery"
        description="Demonstrates the three short-form summaries the assistant can produce on demand. In the live app, these surface as buttons on the employee detail page and the audit explorer — generation is just-in-time, no nightly index needed."
      />

      <PreviewNotice />

      <KpiGrid minColumnWidth={240}>
        <StatCard
          variant="accent-corner"
          tone="primary"
          icon={History}
          label="Timeline summaries"
          value="On demand"
          delta="per employee"
        />
        <StatCard
          variant="accent-corner"
          tone="info"
          icon={ScrollText}
          label="Audit summaries"
          value="On demand"
          delta="per actor / per range"
        />
        <StatCard
          variant="accent-corner"
          tone="success"
          icon={Users}
          label="Team pulses"
          value="Monthly"
          delta="HR-only today"
        />
      </KpiGrid>

      <Section
        title="How this works"
        subtitle="The pattern the live integration will follow."
      >
        <Card
          styles={{
            body: { padding: 18, display: 'flex', flexDirection: 'column', gap: 8 },
          }}
        >
          <Text style={{ fontSize: 13, lineHeight: 1.6 }}>
            On a real request the assistant pulls the relevant rows just-in-time —
            the employee timeline page, an audit slice for the actor, the
            department roster — embeds them, and asks the qwen2.5 instruct model
            to compose a short summary anchored to those facts. Citations point
            back at the source rows so HR can verify before acting on a summary.
          </Text>
          <Text type="secondary" style={{ fontSize: 12 }}>
            Timeline and audit summaries are not pre-indexed nightly — they're
            cheap to generate and easy to keep fresh.
          </Text>
        </Card>
      </Section>

      <Section title="Examples" subtitle="Tap generate on any card to watch the stream.">
        <Row gutter={[16, 16]}>
          {summaryFixtures.map((s) => (
            <Col key={s.id} xs={24} lg={12}>
              <SummaryCard summary={s} />
            </Col>
          ))}
        </Row>
      </Section>
    </Space>
  );
}
