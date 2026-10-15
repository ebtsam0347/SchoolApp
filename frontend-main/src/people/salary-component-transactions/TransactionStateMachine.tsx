// State-machine view of a salary component transaction.
//
// Flow: DRAFT → COMPUTED → APPROVED, with a transient COMPUTING state
// and side branches for Reset, Cancel, Failed.

import type { CSSProperties, ReactNode } from 'react';
import {
  AlertTriangle,
  Ban,
  Check,
  Loader2,
  RotateCcw,
} from 'lucide-react';
import { Tooltip, theme as antdTheme, Typography } from 'antd';

import { StatusPill } from '@/components';
import {
  TRANSACTION_STATUS_LABEL,
  TRANSACTION_STATUS_TONE,
} from './format';
import type { SalaryComponentTransactionStatus } from './types';

const { Text } = Typography;
const { useToken } = antdTheme;

const HAPPY_PATH = ['DRAFT', 'COMPUTED', 'APPROVED'] as const;
type HappyPathState = (typeof HAPPY_PATH)[number];

const TRANSITION_LABELS: Record<HappyPathState, string | null> = {
  DRAFT: null,
  COMPUTED: 'Compute',
  APPROVED: 'Approve',
};

const DESCRIPTIONS: Record<SalaryComponentTransactionStatus, string> = {
  DRAFT: 'Transaction created. Run Compute to begin.',
  LOCKED: 'Attendance frozen for the period. Compute next.',
  COMPUTING: 'Per-employee computation in progress…',
  COMPUTED: 'Lines ready. Review and approve, or reset to fix data.',
  APPROVED: 'Reviewed and signed off. Terminal state.',
  POSTING: 'Generating records and firing per-employee events…',
  POSTED: 'Records generated, events fired. Terminal state.',
  FAILED: 'A transition failed. Inspect the transaction and retry.',
  CANCELLED: 'Cancelled before posting.',
};

export type TransactionStateMachineProps = {
  status: SalaryComponentTransactionStatus;
  actions?: ReactNode;
  lastError?: string | null;
  skippedCount?: number;
  className?: string;
  styles?: { root?: CSSProperties };
};

export function TransactionStateMachine({
  status,
  actions,
  lastError,
  skippedCount = 0,
  className,
  styles,
}: TransactionStateMachineProps) {
  const { token } = useToken();

  const settled: HappyPathState =
    status === 'COMPUTING'
      ? 'DRAFT'
      : status === 'POSTING' || status === 'POSTED'
        ? 'APPROVED'
        : status === 'FAILED' || status === 'CANCELLED'
          ? 'DRAFT'
          : status === 'LOCKED'
            ? 'DRAFT'
            : (status as HappyPathState);
  const settledIdx = HAPPY_PATH.indexOf(settled);
  const transient = status === 'COMPUTING';
  const offPath = status === 'FAILED' || status === 'CANCELLED';
  const tone = TRANSACTION_STATUS_TONE[status];

  return (
    <div
      className={className}
      style={{
        display: 'flex',
        flexDirection: 'column',
        gap: 16,
        padding: 18,
        background: token.colorBgContainer,
        border: `1px solid ${token.colorBorderSecondary}`,
        borderRadius: token.borderRadiusLG,
        ...styles?.root,
      }}
    >
      <header
        style={{
          display: 'flex',
          alignItems: 'flex-start',
          justifyContent: 'space-between',
          gap: 16,
          flexWrap: 'wrap',
        }}
      >
        <div style={{ minWidth: 0, flex: '1 1 240px' }}>
          <Text
            type="secondary"
            style={{
              fontSize: 11,
              textTransform: 'uppercase',
              letterSpacing: '0.06em',
            }}
          >
            Current state
          </Text>
          <div
            style={{
              display: 'flex',
              alignItems: 'center',
              gap: 10,
              marginTop: 4,
              marginBottom: 4,
            }}
          >
            {status !== 'LOCKED' && (
              <StatusPill tone={tone}>
                {TRANSACTION_STATUS_LABEL[status]}
              </StatusPill>
            )}
            {transient ? (
              <Loader2
                size={14}
                strokeWidth={1.75}
                color={token.colorInfo}
                style={{ animation: 'txn-state-spin 1s linear infinite' }}
                aria-hidden
              />
            ) : null}
          </div>
          {status !== 'LOCKED' && (
            <Text type="secondary" style={{ fontSize: 12 }}>
              {DESCRIPTIONS[status]}
            </Text>
          )}
        </div>
        {actions ? (
          <div style={{ display: 'flex', gap: 6, flexWrap: 'wrap', justifyContent: 'flex-end' }}>
            {actions}
          </div>
        ) : null}
      </header>

      <Diagram
        settledIdx={settledIdx}
        status={status}
        transient={transient}
        offPath={offPath}
      />

      {status === 'COMPUTED' && skippedCount > 0 ? (
        <SideRoute
          icon={AlertTriangle}
          tone="warning"
          label={`${skippedCount} employee${skippedCount === 1 ? '' : 's'} skipped`}
          description="Compute could not produce a line for these employees. Use Fix on each row in the Skipped section, then Retry — no full reset needed."
        />
      ) : null}

      {status === 'COMPUTED' ? (
        <SideRoute
          icon={RotateCcw}
          tone="warning"
          label="Reset"
          description="Drops every line on this transaction and rolls back to Locked. Use to fix employee data before recomputing."
        />
      ) : null}

      {status === 'FAILED' ? (
        <SideRoute
          icon={AlertTriangle}
          tone="error"
          label="Failed"
          description={lastError ?? 'A transition failed. Retry from the actions above.'}
          mono={!!lastError}
        />
      ) : null}

      {status === 'CANCELLED' ? (
        <SideRoute
          icon={Ban}
          tone="neutral"
          label="Cancelled"
          description="The transaction was cancelled before posting. Open a new transaction for the same period to start again."
        />
      ) : null}

      <style>{`
        @keyframes txn-state-spin {
          from { transform: rotate(0deg); }
          to   { transform: rotate(360deg); }
        }
        @media (prefers-reduced-motion: reduce) {
          [style*="txn-state-spin"] { animation: none !important; }
        }
        @keyframes txn-state-pulse {
          0%, 100% { box-shadow: 0 0 0 0 var(--txn-state-pulse-color, currentColor); }
          50%      { box-shadow: 0 0 0 6px transparent; }
        }
      `}</style>
    </div>
  );
}

/* ----------------------------- Diagram ------------------------------- */

function Diagram({
  settledIdx,
  status,
  transient,
  offPath,
}: {
  settledIdx: number;
  status: SalaryComponentTransactionStatus;
  transient: boolean;
  offPath: boolean;
}) {
  return (
    <div
      role="img"
      aria-label={`Transaction state: ${TRANSACTION_STATUS_LABEL[status]}`}
      style={{
        display: 'flex',
        alignItems: 'center',
        flexWrap: 'wrap',
        rowGap: 12,
      }}
    >
      {HAPPY_PATH.map((s, i) => {
        const isSettled = i === settledIdx && !transient && !offPath;
        const isPast = i < settledIdx || (i === settledIdx && transient);
        const isTerminal =
          status === 'APPROVED' && i === HAPPY_PATH.length - 1;

        return (
          <div
            key={s}
            style={{ display: 'inline-flex', alignItems: 'center' }}
          >
            <Node
              state={s}
              kind={isSettled ? 'current' : isPast ? 'past' : 'upcoming'}
              terminal={isTerminal}
              dimmed={offPath}
            />
            {i < HAPPY_PATH.length - 1 ? (
              <Edge
                label={TRANSITION_LABELS[HAPPY_PATH[i + 1]]}
                kind={
                  transient && i === settledIdx
                    ? 'active'
                    : i < settledIdx
                      ? 'done'
                      : 'pending'
                }
                dimmed={offPath}
              />
            ) : null}
          </div>
        );
      })}
    </div>
  );
}

function Node({
  state,
  kind,
  terminal,
  dimmed,
}: {
  state: HappyPathState;
  kind: 'current' | 'past' | 'upcoming';
  terminal: boolean;
  dimmed: boolean;
}) {
  const { token } = useToken();
  const tone = TRANSACTION_STATUS_TONE[state];
  const accent =
    tone === 'success'
      ? token.colorSuccess
      : tone === 'warning'
        ? token.colorWarning
        : tone === 'error'
          ? token.colorError
          : tone === 'info'
            ? token.colorInfo
            : token.colorPrimary;

  const ringColor =
    kind === 'current'
      ? accent
      : kind === 'past'
        ? token.colorSuccess
        : token.colorBorderSecondary;
  const fillColor =
    kind === 'current'
      ? `color-mix(in srgb, ${accent} 14%, ${token.colorBgContainer})`
      : kind === 'past'
        ? `color-mix(in srgb, ${token.colorSuccess} 8%, ${token.colorBgContainer})`
        : token.colorBgContainer;
  const labelColor =
    kind === 'current'
      ? token.colorText
      : kind === 'past'
        ? token.colorTextSecondary
        : token.colorTextTertiary;

  return (
    <div
      style={{
        display: 'inline-flex',
        flexDirection: 'column',
        alignItems: 'center',
        gap: 6,
        opacity: dimmed && kind !== 'current' ? 0.4 : 1,
      }}
    >
      <div
        style={{
          width: 36,
          height: 36,
          borderRadius: 999,
          border: `2px solid ${ringColor}`,
          background: fillColor,
          display: 'inline-flex',
          alignItems: 'center',
          justifyContent: 'center',
          color: kind === 'past' ? token.colorSuccess : accent,
          fontWeight: 700,
          fontSize: 12,
          fontVariantNumeric: 'tabular-nums',
          boxShadow:
            kind === 'current'
              ? `0 0 0 4px color-mix(in srgb, ${accent} 16%, transparent)`
              : 'none',
          transition: 'box-shadow 200ms ease',
        }}
      >
        {kind === 'past' ? <Check size={16} strokeWidth={2.25} /> : null}
        {kind === 'current' ? <span aria-hidden>●</span> : null}
        {kind === 'upcoming' ? <span aria-hidden>○</span> : null}
      </div>
      <div
        style={{
          fontSize: 11,
          fontWeight: kind === 'current' ? 600 : 500,
          color: labelColor,
          letterSpacing: '0.02em',
          textAlign: 'center',
          maxWidth: 80,
          lineHeight: 1.2,
        }}
      >
        {TRANSACTION_STATUS_LABEL[state]}
        {terminal ? (
          <Tooltip title="Terminal state — no further transitions.">
            <span style={{ marginLeft: 4, opacity: 0.6 }}>·</span>
          </Tooltip>
        ) : null}
      </div>
    </div>
  );
}

function Edge({
  label,
  kind,
  dimmed,
}: {
  label: string | null;
  kind: 'done' | 'active' | 'pending';
  dimmed: boolean;
}) {
  const { token } = useToken();
  const lineColor =
    kind === 'done'
      ? token.colorSuccess
      : kind === 'active'
        ? token.colorInfo
        : token.colorBorderSecondary;
  const labelColor =
    kind === 'done'
      ? token.colorTextSecondary
      : kind === 'active'
        ? token.colorInfo
        : token.colorTextTertiary;

  return (
    <div
      style={{
        display: 'inline-flex',
        flexDirection: 'column',
        alignItems: 'center',
        margin: '0 4px',
        minWidth: 56,
        opacity: dimmed ? 0.4 : 1,
      }}
    >
      <div
        style={{
          height: 2,
          width: '100%',
          background:
            kind === 'active'
              ? `repeating-linear-gradient(90deg, ${lineColor} 0 6px, transparent 6px 10px)`
              : lineColor,
          borderRadius: 1,
          minHeight: 2,
          marginTop: 18,
        }}
      />
      {label ? (
        <Text
          style={{
            fontSize: 10,
            color: labelColor,
            marginTop: 4,
            textTransform: 'uppercase',
            letterSpacing: '0.06em',
            whiteSpace: 'nowrap',
          }}
        >
          {label}
        </Text>
      ) : null}
    </div>
  );
}

/* ----------------------------- Side route ----------------------------- */

function SideRoute({
  icon: Icon,
  tone,
  label,
  description,
  mono,
}: {
  icon: typeof RotateCcw;
  tone: 'warning' | 'error' | 'neutral';
  label: string;
  description: string;
  mono?: boolean;
}) {
  const { token } = useToken();
  const accent =
    tone === 'warning'
      ? token.colorWarning
      : tone === 'error'
        ? token.colorError
        : token.colorTextTertiary;

  return (
    <div
      style={{
        display: 'flex',
        alignItems: 'flex-start',
        gap: 10,
        padding: 10,
        borderRadius: 8,
        background: `color-mix(in srgb, ${accent} 8%, transparent)`,
        border: `1px solid color-mix(in srgb, ${accent} 24%, transparent)`,
      }}
    >
      <Icon
        size={16}
        strokeWidth={1.75}
        color={accent}
        style={{ flexShrink: 0, marginTop: 1 }}
        aria-hidden
      />
      <div style={{ minWidth: 0 }}>
        <div
          style={{
            fontSize: 12,
            fontWeight: 600,
            color: token.colorText,
            marginBottom: 2,
          }}
        >
          {label}
        </div>
        <div
          style={{
            fontSize: 11,
            color: token.colorTextSecondary,
            fontFamily: mono
              ? 'ui-monospace, SFMono-Regular, Menlo, monospace'
              : undefined,
            wordBreak: 'break-word',
          }}
        >
          {description}
        </div>
      </div>
    </div>
  );
}
