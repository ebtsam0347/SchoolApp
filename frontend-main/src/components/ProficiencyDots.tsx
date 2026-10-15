import { theme as antdTheme } from 'antd';
import type { CSSProperties } from 'react';

const { useToken } = antdTheme;

export type ProficiencyLevel = 'BEGINNER' | 'INTERMEDIATE' | 'ADVANCED' | 'EXPERT';

export type ProficiencyDotsProps = {
  level: ProficiencyLevel;
  /** Diameter of each dot in px. Default 6. */
  size?: number;
  /** Show the level name beside the dots. Default false. */
  showLabel?: boolean;
  className?: string;
  style?: CSSProperties;
};

const FILLED: Record<ProficiencyLevel, number> = {
  BEGINNER: 1,
  INTERMEDIATE: 2,
  ADVANCED: 3,
  EXPERT: 4,
};

const LABELS: Record<ProficiencyLevel, string> = {
  BEGINNER: 'Beginner',
  INTERMEDIATE: 'Intermediate',
  ADVANCED: 'Advanced',
  EXPERT: 'Expert',
};

export function ProficiencyDots({
  level,
  size = 6,
  showLabel = false,
  className,
  style,
}: ProficiencyDotsProps) {
  const { token } = useToken();
  const filled = FILLED[level];
  const total = 4;

  return (
    <span
      className={className}
      style={{
        display: 'inline-flex',
        alignItems: 'center',
        gap: 6,
        ...style,
      }}
      aria-label={`Proficiency: ${LABELS[level]}`}
    >
      <span style={{ display: 'inline-flex', gap: 3 }}>
        {Array.from({ length: total }, (_, i) => (
          <span
            key={i}
            style={{
              width: size,
              height: size,
              borderRadius: '50%',
              background:
                i < filled ? token.colorPrimary : token.colorBorderSecondary,
              display: 'inline-block',
            }}
          />
        ))}
      </span>
      {showLabel ? (
        <span style={{ fontSize: 12, color: token.colorTextSecondary }}>
          {LABELS[level]}
        </span>
      ) : null}
    </span>
  );
}
