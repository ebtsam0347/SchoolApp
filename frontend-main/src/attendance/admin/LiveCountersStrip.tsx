// Org-wide live counters for the monitoring page. Each figure rolls up/down
// smoothly (AnimatedNumber) as fresh data lands — so as employees punch in the
// "Present" count visibly ticks 245 → 246 without a page refresh. A pulsing
// LIVE badge signals the page is auto-updating.
//
// Figures come from the operator-dashboard pulse (org-wide, unfiltered) — the
// page polls it and, once the backend forwards attendance.* over SSE, refetches
// instantly on each punch.

import { Typography, theme as antdTheme } from 'antd';
import { CheckCircle2, Fingerprint, Plane, UserMinus } from 'lucide-react';

import { KpiGrid, StatCard } from '@/components';
import type { OperatorDashboardResponse } from '@/attendance';
import { AnimatedNumber } from './AnimatedNumber';

const { Text } = Typography;
const { useToken } = antdTheme;

export type LiveCountersStripProps = {
  data: OperatorDashboardResponse | undefined;
  loading: boolean;
  /** Drives the LIVE badge's "updating" pulse. */
  fetching: boolean;
};

export function LiveCountersStrip({ data, loading, fetching }: LiveCountersStripProps) {
  const { token } = useToken();
  const pulse = data?.pulse;

  const num = (v: number | undefined) =>
    loading && v === undefined ? (
      <span style={{ fontVariantNumeric: 'tabular-nums' }}>—</span>
    ) : (
      <AnimatedNumber value={v ?? 0} />
    );

  return (
    <div style={{ display: 'flex', flexDirection: 'column', gap: 12 }}>
      <div style={{ display: 'flex', alignItems: 'center', gap: 8 }}>
        <span
          aria-hidden
          className={fetching ? 'fs-live-dot fs-live-dot-active' : 'fs-live-dot'}
          style={{
            width: 9,
            height: 9,
            borderRadius: '50%',
            background: token.colorSuccess,
          }}
        />
        <Text
          strong
          style={{
            fontSize: 12,
            letterSpacing: '0.08em',
            textTransform: 'uppercase',
            color: token.colorSuccess,
          }}
        >
          Live
        </Text>
        <Text type="secondary" style={{ fontSize: 12 }}>
          {fetching ? 'Updating…' : 'Auto-updating as attendance is marked'}
        </Text>
      </div>

      <KpiGrid minColumnWidth={200}>
        <StatCard
          variant="hero"
          tone="success"
          icon={CheckCircle2}
          label="Present now"
          value={num(pulse?.present)}
          delta={
            pulse ? `of ${pulse.total.toLocaleString('en-PK')} employees` : undefined
          }
        />
        <StatCard
          variant="plain"
          tone="info"
          icon={Plane}
          label="On leave"
          value={num(pulse?.onLeave)}
        />
        <StatCard
          variant="plain"
          tone="error"
          icon={UserMinus}
          label="Absent"
          value={num(pulse?.absent)}
        />
        <StatCard
          variant="accent-radial"
          tone="primary"
          accentCorner="tr"
          icon={Fingerprint}
          label="Punches today"
          value={num(data?.totalPunchesToday)}
          delta="across every source"
        />
      </KpiGrid>
    </div>
  );
}
