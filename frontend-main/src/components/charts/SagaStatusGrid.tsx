import { Typography, theme as antdTheme } from 'antd';
import type { CSSProperties, ReactNode } from 'react';

import { BarSpark } from './BarSpark';
import { ChartEmpty } from './ChartEmpty';
import { alphaMix, type ChartTone } from './theme';

const { Text } = Typography;
const { useToken } = antdTheme;

export type SagaHealth = 'OK' | 'DEGRADED' | 'INCIDENT' | 'UNKNOWN';

export interface SagaStatusRow {
  /** Stable ID — also doubles as React key. */
  id: string;
  /** Display name (e.g. "Salary revision"). */
  name: string;
  status: SagaHealth;
  /** 24 hourly failure-rate buckets (newest last). */
  failureRate24h: number[];
  /** Optional caption (e.g. "0 failures · last 24h"). Defaults to a status-derived line. */
  caption?: ReactNode;
  /** Optional click target — the whole card becomes a link. */
  href?: string;
  /** Optional inline action slot (e.g. retry button). Mutually exclusive with `href`. */
  actions?: ReactNode;
}

export interface SagaStatusGridProps {
  rows: SagaStatusRow[];
  /** Min column width for the responsive grid. Default 280. */
  minColumnWidth?: number;
  emptyTitle?: ReactNode;
  emptyDescription?: ReactNode;
  className?: string;
  styles?: { root?: CSSProperties };
}

/**
 * Operator-tier saga health view. Each card carries a saga's name,
 * a status pill, and a 24h failure-rate sparkline. Card tone follows
 * the worst-case status so a degraded saga is visible at a glance
 * even when scanning past the name.
 */
export function SagaStatusGrid({
  rows,
  minColumnWidth = 280,
  emptyTitle = 'No sagas registered',
  emptyDescription,
  className,
  styles: slot,
}: SagaStatusGridProps) {
  if (!rows || rows.length === 0) {
    return (
      <div className={className} style={{ flex: 1, display: 'flex', ...slot?.root }}>
        <ChartEmpty title={emptyTitle} description={emptyDescription} />
      </div>
    );
  }

  return (
    <div
      className={className}
      style={{
        display: 'grid',
        gap: 12,
        gridTemplateColumns: `repeat(auto-fit, minmax(${minColumnWidth}px, 1fr))`,
        width: '100%',
        ...slot?.root,
      }}
    >
      {rows.map((row) => (
        <SagaCard key={row.id} row={row} />
      ))}
    </div>
  );
}

function SagaCard({ row }: { row: SagaStatusRow }) {
  const { token } = useToken();
  const tone = toneFor(row.status);
  const toneColor = tokenToneColor(tone, token);

  const totalFailures = row.failureRate24h.reduce((a, b) => a + b, 0);
  const captionDefault =
    row.status === 'OK'
      ? `${totalFailures === 0 ? 'No' : totalFailures} failure${totalFailures === 1 ? '' : 's'} · last 24h`
      : row.status === 'DEGRADED'
        ? `${totalFailures} failure${totalFailures === 1 ? '' : 's'} in last 24h`
        : row.status === 'INCIDENT'
          ? `${totalFailures} failure${totalFailures === 1 ? '' : 's'} · investigate`
          : 'No data';

  const body = (
    <div
      style={{
        padding: 14,
        borderRadius: token.borderRadius,
        background: tone === 'success' ? token.colorBgContainer : alphaMix(toneColor, 6),
        border: `1px solid ${tone === 'success' ? token.colorBorderSecondary : alphaMix(toneColor, 24)}`,
        display: 'flex',
        flexDirection: 'column',
        gap: 10,
        transition: 'border-color 160ms ease',
      }}
    >
      <div style={{ display: 'flex', alignItems: 'flex-start', gap: 10 }}>
        <span
          aria-hidden
          style={{
            width: 10,
            height: 10,
            borderRadius: '50%',
            background: toneColor,
            boxShadow: `0 0 0 3px ${alphaMix(toneColor, 22)}`,
            marginTop: 5,
            flexShrink: 0,
          }}
        />
        <div style={{ minWidth: 0, flex: 1 }}>
          <div
            style={{
              fontSize: 14,
              fontWeight: 600,
              color: token.colorTextHeading,
              lineHeight: 1.3,
              overflow: 'hidden',
              textOverflow: 'ellipsis',
              whiteSpace: 'nowrap',
            }}
          >
            {row.name}
          </div>
          <Text
            type="secondary"
            style={{ fontSize: 12, display: 'block', marginTop: 2 }}
          >
            {row.caption ?? captionDefault}
          </Text>
        </div>
        <StatusLabel status={row.status} tone={tone} />
      </div>

      <div
        style={{
          display: 'flex',
          alignItems: 'center',
          justifyContent: 'space-between',
          gap: 12,
        }}
      >
        <BarSpark data={row.failureRate24h} tone={tone} height={24} width="100%" />
      </div>

      {row.actions ? (
        <div style={{ display: 'flex', justifyContent: 'flex-end', gap: 6 }}>
          {row.actions}
        </div>
      ) : null}
    </div>
  );

  if (row.href && !row.actions) {
    return (
      <a
        href={row.href}
        className="fs-focus-ring"
        style={{ color: 'inherit', textDecoration: 'none', display: 'block' }}
      >
        {body}
      </a>
    );
  }
  return body;
}

function StatusLabel({ status, tone }: { status: SagaHealth; tone: ChartTone }) {
  const { token } = useToken();
  const color = tokenToneColor(tone, token);
  const label =
    status === 'OK'
      ? 'OK'
      : status === 'DEGRADED'
        ? 'Degraded'
        : status === 'INCIDENT'
          ? 'Incident'
          : 'Unknown';
  return (
    <span
      style={{
        fontSize: 11,
        fontWeight: 600,
        textTransform: 'uppercase',
        letterSpacing: '0.06em',
        color,
        background: alphaMix(color, 14),
        padding: '2px 8px',
        borderRadius: 4,
        flexShrink: 0,
      }}
    >
      {label}
    </span>
  );
}

function toneFor(status: SagaHealth): ChartTone {
  switch (status) {
    case 'OK':
      return 'success';
    case 'DEGRADED':
      return 'warning';
    case 'INCIDENT':
      return 'error';
    case 'UNKNOWN':
    default:
      return 'neutral';
  }
}

function tokenToneColor(
  tone: ChartTone,
  token: ReturnType<typeof antdTheme.useToken>['token'],
): string {
  switch (tone) {
    case 'primary':
      return token.colorPrimary;
    case 'success':
      return token.colorSuccess;
    case 'warning':
      return token.colorWarning;
    case 'error':
      return token.colorError;
    case 'info':
      return token.colorInfo;
    case 'neutral':
    default:
      return token.colorTextTertiary;
  }
}
