// Donut breakdown of today's org attendance, derived from the operator
// dashboard's pulse (no extra round-trip). Sits beside the KPI grid on the
// /attendance dashboard. Present / Absent / Leave / Half-day / Unmarked; the
// holiday+weekend "off duty" figure is shown as a footnote rather than a slice.

import { Card, Skeleton, Typography, theme as antdTheme } from 'antd';
import { MetricRing, useChartTheme } from '@/components/charts';
import type { OrgPulseView } from '../bff-api';
import { deriveDistribution } from './dashboard-live';

const { Text } = Typography;
const { useToken } = antdTheme;

export type DistributionCardProps = {
  pulse: OrgPulseView | undefined;
  loading: boolean;
};

export function DistributionCard({ pulse, loading }: DistributionCardProps) {
  const { token } = useToken();
  const chart = useChartTheme();
  const { slices, total, offDuty } = deriveDistribution(pulse);

  const present = slices.find((s) => s.key === 'present')?.value ?? 0;
  const pct = (v: number): string =>
    total > 0 ? `${Math.round((v / total) * 100)}%` : '—';

  return (
    <Card
      styles={{ body: { padding: 18 } }}
      style={{ height: '100%' }}
      title={<span style={{ fontSize: 14, fontWeight: 600 }}>Attendance distribution</span>}
    >
      {loading && !pulse ? (
        <Skeleton active paragraph={{ rows: 3 }} />
      ) : (
        <div
          style={{
            display: 'flex',
            gap: 20,
            alignItems: 'center',
            flexWrap: 'wrap',
          }}
        >
          <MetricRing
            size={148}
            segments={slices.map((s) => ({ value: s.value, tone: s.tone, label: s.label }))}
            label={present.toLocaleString('en-PK')}
            caption="Present"
            tooltip
            tooltipValueFormatter={(v, name) => `${name}: ${v.toLocaleString('en-PK')}`}
          />

          <ul
            style={{
              listStyle: 'none',
              margin: 0,
              padding: 0,
              flex: 1,
              minWidth: 180,
              display: 'flex',
              flexDirection: 'column',
              gap: 8,
            }}
          >
            {slices.map((s) => (
              <li
                key={s.key}
                style={{ display: 'flex', alignItems: 'center', gap: 10 }}
              >
                <span
                  aria-hidden
                  style={{
                    width: 10,
                    height: 10,
                    borderRadius: 3,
                    background: chart.toneColor(s.tone),
                    flexShrink: 0,
                  }}
                />
                <span style={{ fontSize: 13, color: token.colorText, flex: 1 }}>
                  {s.label}
                </span>
                <span
                  style={{
                    fontSize: 13,
                    fontWeight: 600,
                    fontVariantNumeric: 'tabular-nums',
                    color: token.colorTextHeading,
                  }}
                >
                  {s.value.toLocaleString('en-PK')}
                </span>
                <span
                  style={{
                    fontSize: 12,
                    color: token.colorTextTertiary,
                    fontVariantNumeric: 'tabular-nums',
                    width: 40,
                    textAlign: 'right',
                  }}
                >
                  {pct(s.value)}
                </span>
              </li>
            ))}
          </ul>
        </div>
      )}

      <Text type="secondary" style={{ display: 'block', marginTop: 12, fontSize: 12 }}>
        {total.toLocaleString('en-PK')} employees ·{' '}
        {offDuty.toLocaleString('en-PK')} on holiday / weekend
      </Text>
    </Card>
  );
}
