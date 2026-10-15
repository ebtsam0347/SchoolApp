import type { CSSProperties } from 'react';

import { StatusPill, type StatusPillTone } from './StatusPill';

export type WarningLevel = 'FIRST_WARNING' | 'SECOND_WARNING' | 'FINAL_WARNING';
export type WarningStatus = 'ISSUED' | 'CLOSED';
export type WarningDecision = 'UPHELD' | 'WITHDRAWN' | null;

export type WarningPillProps = {
  level: WarningLevel;
  status?: WarningStatus;
  decision?: WarningDecision;
  /** Compact for table rows, default for card headers. */
  size?: 'sm' | 'md';
  className?: string;
  style?: CSSProperties;
};

const LEVEL_LABEL: Record<WarningLevel, string> = {
  FIRST_WARNING: '1st warning',
  SECOND_WARNING: '2nd warning',
  FINAL_WARNING: 'Final warning',
};

const LEVEL_TONE: Record<WarningLevel, StatusPillTone> = {
  FIRST_WARNING: 'warning',
  SECOND_WARNING: 'warning',
  FINAL_WARNING: 'error',
};

export function WarningPill({
  level,
  status,
  decision,
  size = 'sm',
  className,
  style,
}: WarningPillProps) {
  // WITHDRAWN dims to neutral — it doesn't count toward FY totals and shouldn't
  // visually compete with active marks. UPHELD keeps the level tone.
  const tone: StatusPillTone =
    decision === 'WITHDRAWN' ? 'neutral' : LEVEL_TONE[level];

  // Suffix communicates the row's lifecycle distinctly from the level itself.
  const suffix =
    decision === 'WITHDRAWN'
      ? '· Withdrawn'
      : decision === 'UPHELD'
        ? '· Upheld'
        : status === 'ISSUED'
          ? '· Pending'
          : null;

  return (
    <StatusPill tone={tone} size={size} className={className} style={style}>
      {LEVEL_LABEL[level]}
      {suffix ? <span style={{ opacity: 0.75, marginLeft: 4 }}>{suffix}</span> : null}
    </StatusPill>
  );
}
