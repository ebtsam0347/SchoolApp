// Right-side card on /time/today: leave balances strip + Apply link.

import { Card, theme as antdTheme, Typography } from 'antd';
import { Link } from '@tanstack/react-router';
import { ArrowRight } from 'lucide-react';

const { Text } = Typography;
const { useToken } = antdTheme;

export type BalanceSummaryCardProps = {
  summary:
    | { annualAvailable: number; sickAvailable: number; casualAvailable: number }
    | undefined;
  loading: boolean;
};

export function BalanceSummaryCard({ summary, loading }: BalanceSummaryCardProps) {
  const { token } = useToken();

  return (
    <Card
      styles={{
        body: {
          padding: 22,
          height: '100%',
          display: 'flex',
          flexDirection: 'column',
          gap: 14,
        },
      }}
      style={{ height: '100%' }}
    >
      <header
        style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center' }}
      >
        <div>
          <div
            style={{
              fontSize: 11,
              textTransform: 'uppercase',
              letterSpacing: '0.08em',
              color: token.colorTextTertiary,
              fontWeight: 600,
            }}
          >
            Leave balances
          </div>
          <Text style={{ fontSize: 14, fontWeight: 600 }}>{new Date().getFullYear()}</Text>
        </div>
        <Link
          to="/time/leaves"
          style={{
            fontSize: 12,
            display: 'inline-flex',
            alignItems: 'center',
            gap: 4,
            color: token.colorPrimary,
          }}
        >
          Apply <ArrowRight size={12} />
        </Link>
      </header>

      {loading ? (
        <Text type="secondary">Loading…</Text>
      ) : !summary ? (
        <Text type="secondary">No leave balances configured.</Text>
      ) : (
        <div style={{ display: 'flex', flexDirection: 'column', gap: 12 }}>
          <BalanceLine label="Annual" days={summary.annualAvailable} />
          <BalanceLine label="Sick" days={summary.sickAvailable} />
          <BalanceLine label="Casual" days={summary.casualAvailable} />
        </div>
      )}
    </Card>
  );
}

function BalanceLine({ label, days }: { label: string; days: number }) {
  const { token } = useToken();
  const low = days <= 1;
  return (
    <div
      style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'baseline' }}
    >
      <Text style={{ fontSize: 13, fontWeight: 500 }}>{label}</Text>
      <Text style={{ fontSize: 14, fontVariantNumeric: 'tabular-nums' }}>
        <strong style={{ color: low ? token.colorWarning : token.colorText }}>
          {days.toFixed(1)}
        </strong>
        <span style={{ color: token.colorTextTertiary, marginLeft: 4, fontSize: 12 }}>
          d available
        </span>
      </Text>
    </div>
  );
}
