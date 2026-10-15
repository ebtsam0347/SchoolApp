import { theme as antdTheme } from 'antd';
import type { CSSProperties, ReactNode } from 'react';

const { useToken } = antdTheme;

export type StatusPillTone =
  | 'primary'
  | 'success'
  | 'warning'
  | 'error'
  | 'info'
  | 'neutral';

export type StatusPillProps = {
  /** Display label. The tone-color decides the surface tint. */
  children: ReactNode;
  tone?: StatusPillTone;
  /** Compact for table rows, default for card headers. */
  size?: 'sm' | 'md';
  /** Optional leading icon node. */
  icon?: ReactNode;
  /** Use a stronger filled treatment instead of the default tinted-surface. */
  variant?: 'soft' | 'solid';
  className?: string;
  style?: CSSProperties;
};

export function StatusPill({
  children,
  tone = 'neutral',
  size = 'sm',
  icon,
  variant = 'soft',
  className,
  style,
}: StatusPillProps) {
  const { token } = useToken();

  const toneColors: Record<StatusPillTone, string> = {
    primary: token.colorPrimary,
    success: token.colorSuccess,
    warning: token.colorWarning,
    error: token.colorError,
    info: token.colorInfo,
    neutral: token.colorTextTertiary,
  };
  const toneColor = toneColors[tone];

  const isSolid = variant === 'solid';
  const heightPx = size === 'sm' ? 22 : 26;
  const fontSize = size === 'sm' ? 11 : 12;
  const padX = size === 'sm' ? 8 : 10;

  return (
    <span
      className={className}
      style={{
        display: 'inline-flex',
        alignItems: 'center',
        gap: 5,
        height: heightPx,
        padding: `0 ${padX}px`,
        fontSize,
        fontWeight: 600,
        letterSpacing: '0.02em',
        lineHeight: 1,
        borderRadius: 999,
        whiteSpace: 'nowrap',
        color: isSolid ? token.colorTextLightSolid : toneColor,
        background: isSolid
          ? toneColor
          : `color-mix(in srgb, ${toneColor} 14%, transparent)`,
        border: isSolid
          ? 'none'
          : `1px solid color-mix(in srgb, ${toneColor} 28%, transparent)`,
        ...style,
      }}
    >
      {icon ? <span style={{ display: 'inline-flex' }}>{icon}</span> : null}
      {children}
    </span>
  );
}
