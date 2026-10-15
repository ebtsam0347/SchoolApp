// State-machine view of a payroll run.
//
// Payroll runs are *not* a saga — they're a state machine with discrete
// states and named transitions, including a loop (COMPUTED ─reset→ LOCKED).
// The visualization reflects that: nodes for each canonical state, labeled
// edges for the transition that moves between them, and side branches for
// Reset (loop), Cancel (off-path), and Fail (transient → off-path).
//
// Transient states (COMPUTING / POSTING) render between their source and
// target node with a spinner on the edge, signalling "the system is moving."
//
// The happy path, labels and copy come from `config` (see ./machine.ts) so
// other run-shaped flows reuse the same visualization — bonus runs pass a
// shorter DRAFT → COMPUTED → APPROVED path. Defaults to the payroll run.
//
// The actions slot is the parent's responsibility — this component is purely
// presentational.

import type { CSSProperties, ReactNode } from 'react';
import {
  AlertTriangle,
  Ban,
  Check,
  Loader2,
  RotateCcw,
} from 'lucide-react';
import { Tooltip, theme as antdTheme, Typography } from 'antd';

import { StatusPill, type StatusPillTone } from '@/components';
import type { PayrollRunStatus } from '../types';
import { PAYROLL_RUN_MACHINE, type RunStateMachineConfig } from './machine';

const { Text } = Typography;
const { useToken } = antdTheme;

/** Transient states animate on the edge leading into this path state. */
const TRANSIENT_TARGET: Partial<Record<PayrollRunStatus, PayrollRunStatus>> = {
  COMPUTING: 'COMPUTED',
  POSTING: 'POSTED',
};

export type RunStateMachineProps = {
  status: PayrollRunStatus;
  /** Path, labels and copy. Defaults to the payroll-run machine. */
  config?: RunStateMachineConfig;
  /** Right-aligned action buttons for the current state. */
  actions?: ReactNode;
  /** Last server-side error, shown in the FAILED branch. */
  lastError?: string | null;
  /** Employees compute could not produce a payslip for. Surfaced as a
   *  warning side-route in COMPUTED — the canonical fix-and-retry signal. */
  skippedCount?: number;
  className?: string;
  styles?: { root?: CSSProperties };
};

export function RunStateMachine({
  status,
  config = PAYROLL_RUN_MACHINE,
  actions,
  lastError,
  skippedCount = 0,
  className,
  styles,
}: RunStateMachineProps) {
  const { token } = useToken();
  const { path } = config;

  // What the visualization treats as the "settled" position. Transient states
  // sit on the edge between two nodes: the node before their target is
  // settled and the edge into the target animates.
  const transientTarget = TRANSIENT_TARGET[status];
  const targetIdx = transientTarget ? path.indexOf(transientTarget) : -1;
  const transient = targetIdx > 0;
  const busy = transientTarget != null;
  const offPath = status === 'FAILED' || status === 'CANCELLED';
  const settledIdx = transient
    ? targetIdx - 1
    : offPath
      ? 0
      : Math.max(0, path.indexOf(status));
  const tone = config.tones[status];

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
            <StatusPill tone={tone}>
              {config.labels[status]}
            </StatusPill>
            {busy ? (
              <Loader2
                size={14}
                strokeWidth={1.75}
                color={token.colorInfo}
                style={{ animation: 'run-state-spin 1s linear infinite' }}
                aria-hidden
              />
            ) : null}
          </div>
          <Text type="secondary" style={{ fontSize: 12 }}>
            {config.descriptions[status]}
          </Text>
        </div>
        {actions ? (
          <div style={{ display: 'flex', gap: 6, flexWrap: 'wrap', justifyContent: 'flex-end' }}>
            {actions}
          </div>
        ) : null}
      </header>

      <Diagram
        config={config}
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
          description="Compute could not produce a payslip for these employees. Use Fix on each row in the Skipped section, then Retry — no full reset needed."
        />
      ) : null}

      {status === 'COMPUTED' ? (
        <SideRoute
          icon={RotateCcw}
          tone="warning"
          label="Reset"
          description={config.resetDescription}
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
          description={config.cancelledDescription}
        />
      ) : null}

      <style>{`
        @keyframes run-state-spin {
          from { transform: rotate(0deg); }
          to   { transform: rotate(360deg); }
        }
        @media (prefers-reduced-motion: reduce) {
          [style*="run-state-spin"] { animation: none !important; }
        }
        @keyframes run-state-pulse {
          0%, 100% { box-shadow: 0 0 0 0 var(--run-state-pulse-color, currentColor); }
          50%      { box-shadow: 0 0 0 6px transparent; }
        }
      `}</style>
    </div>
  );
}

/* ----------------------------- Diagram ------------------------------- */

function Diagram({
  config,
  settledIdx,
  status,
  transient,
  offPath,
}: {
  config: RunStateMachineConfig;
  settledIdx: number;
  status: PayrollRunStatus;
  transient: boolean;
  offPath: boolean;
}) {
  const { path } = config;
  const lastIdx = path.length - 1;
  const atTerminal = status === path[lastIdx];

  return (
    <div
      role="img"
      aria-label={`Run state: ${config.labels[status]}`}
      style={{
        display: 'flex',
        alignItems: 'center',
        flexWrap: 'wrap',
        rowGap: 12,
      }}
    >
      {path.map((s, i) => {
        const isSettled = i === settledIdx && !transient && !offPath;
        const isPast =
          (i < settledIdx) ||
          (i === settledIdx && (transient || atTerminal));
        const isTerminal = atTerminal && i === lastIdx;

        return (
          <div
            key={s}
            style={{ display: 'inline-flex', alignItems: 'center' }}
          >
            <Node
              label={config.labels[s]}
              tone={config.tones[s]}
              kind={isSettled ? 'current' : isPast ? 'past' : 'upcoming'}
              terminal={isTerminal}
              dimmed={offPath}
            />
            {i < lastIdx ? (
              <Edge
                label={config.transitionLabels[path[i + 1]] ?? null}
                kind={
                  // Transient state animates on the edge it's currently traversing
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
  label,
  tone,
  kind,
  terminal,
  dimmed,
}: {
  label: string;
  tone: StatusPillTone;
  kind: 'current' | 'past' | 'upcoming';
  terminal: boolean;
  dimmed: boolean;
}) {
  const { token } = useToken();
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
        {label}
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
