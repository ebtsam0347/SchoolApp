import { Card, theme as antdTheme } from 'antd';
import type { CSSProperties, ReactNode } from 'react';
import { TrendingDown, TrendingUp, type LucideIcon } from 'lucide-react';

import './StatCard.css';

const { useToken } = antdTheme;

export type StatCardTone =
  | 'primary'
  | 'success'
  | 'warning'
  | 'error'
  | 'info'
  | 'neutral';

export type StatCardVariant =
  | 'plain'
  | 'accent-border'
  | 'accent-corner'
  | 'accent-radial'
  | 'hero';

export type StatCardProps = {
  label: string;
  value: ReactNode;
  delta?: ReactNode;
  icon?: LucideIcon;
  trend?: 'up' | 'down' | 'neutral';
  tone?: StatCardTone;
  variant?: StatCardVariant;
  /** accent-border only */
  accentSide?: 'left' | 'right' | 'top' | 'bottom';
  /** accent-corner / accent-radial only */
  accentCorner?: 'tl' | 'tr' | 'bl' | 'br';
  hoverable?: boolean;
  className?: string;
  styles?: { root?: CSSProperties; body?: CSSProperties };
};

export function StatCard({
  label,
  value,
  delta,
  icon: Icon,
  trend = 'neutral',
  tone = 'neutral',
  variant = 'plain',
  accentSide = 'left',
  accentCorner = 'tr',
  hoverable = true,
  className,
  styles: slot,
}: StatCardProps) {
  const { token } = useToken();

  const toneColor =
    tone === 'primary'
      ? token.colorPrimary
      : tone === 'success'
        ? token.colorSuccess
        : tone === 'warning'
          ? token.colorWarning
          : tone === 'error'
            ? token.colorError
            : tone === 'info'
              ? token.colorInfo
              : token.colorTextTertiary;

  const isHero = variant === 'hero';
  const isRadial = variant === 'accent-radial';
  const isPremium = isHero || isRadial;
  const onSolid = token.colorTextLightSolid;

  const trendColor = isHero
    ? onSolid
    : trend === 'up'
      ? token.colorSuccess
      : trend === 'down'
        ? token.colorError
        : token.colorTextTertiary;

  const TrendIcon = trend === 'up' ? TrendingUp : trend === 'down' ? TrendingDown : null;

  const rootStyle: CSSProperties = {
    // Expose the resolved tone color to the CSS layer; per-corner positions
    // for accent-radial live in StatCard.css under [data-corner=…] selectors.
    ['--stat-tone' as never]: toneColor,
    ...slot?.root,
  };

  const labelColor = isHero ? 'rgba(255, 255, 255, 0.78)' : token.colorTextSecondary;
  const valueColor = isHero ? onSolid : token.colorTextHeading;
  const iconColor = isHero
    ? onSolid
    : tone === 'neutral'
      ? token.colorTextTertiary
      : toneColor;
  const deltaColor = isHero ? 'rgba(255, 255, 255, 0.85)' : token.colorTextSecondary;
  const valueSize = isPremium ? token.fontSizeHeading2 : token.fontSizeHeading3;
  const bodyPadding = isPremium ? 22 : 18;

  // Premium layout (hero / accent-radial): icon-in-badge on the left, uppercase
  // label beside it, large value below. Other variants keep the utility
  // layout (label left + icon right).
  if (isPremium) {
    return (
      <Card
        className={`stat-card${className ? ` ${className}` : ''}`}
        data-variant={variant}
        data-side={accentSide}
        data-corner={accentCorner}
        data-hoverable={hoverable ? 'true' : 'false'}
        data-tone={tone}
        style={rootStyle}
        styles={{
          body: {
            padding: bodyPadding,
            position: 'relative',
            zIndex: 1,
            ...slot?.body,
          },
        }}
      >
        <div style={{ display: 'flex', alignItems: 'center', gap: 10 }}>
          {Icon ? (
            <div
              style={{
                width: 36,
                height: 36,
                borderRadius: 8,
                display: 'flex',
                alignItems: 'center',
                justifyContent: 'center',
                background: isHero
                  ? 'rgba(255, 255, 255, 0.18)'
                  : `color-mix(in srgb, ${toneColor} 14%, transparent)`,
                flexShrink: 0,
              }}
              aria-hidden
            >
              <Icon size={18} strokeWidth={2} color={iconColor} />
            </div>
          ) : null}
          <span
            style={{
              fontSize: 11,
              fontWeight: 600,
              letterSpacing: '0.08em',
              textTransform: 'uppercase',
              color: labelColor,
            }}
          >
            {label}
          </span>
        </div>

        <div
          style={{
            marginTop: 14,
            fontSize: valueSize,
            fontWeight: 700,
            color: valueColor,
            fontVariantNumeric: 'tabular-nums',
            lineHeight: 1.1,
            letterSpacing: '-0.01em',
          }}
        >
          {value}
        </div>

        {delta ? (
          <div
            style={{
              marginTop: 8,
              display: 'flex',
              alignItems: 'center',
              gap: 4,
              fontSize: 13,
              fontWeight: 500,
              color: trendColor,
            }}
          >
            {TrendIcon ? (
              <TrendIcon size={14} strokeWidth={2} aria-hidden />
            ) : null}
            <span style={{ color: isHero ? deltaColor : trendColor }}>{delta}</span>
          </div>
        ) : null}
      </Card>
    );
  }

  // Utility layout — plain / accent-border / accent-corner
  return (
    <Card
      className={`stat-card${className ? ` ${className}` : ''}`}
      data-variant={variant}
      data-side={accentSide}
      data-corner={accentCorner}
      data-hoverable={hoverable ? 'true' : 'false'}
      data-tone={tone}
      style={rootStyle}
      styles={{
        body: {
          padding: 18,
          position: 'relative',
          zIndex: 1,
          ...slot?.body,
        },
      }}
    >
      <div
        style={{
          display: 'flex',
          alignItems: 'center',
          justifyContent: 'space-between',
          gap: 12,
        }}
      >
        <span style={{ fontSize: 12, color: token.colorTextSecondary, letterSpacing: 0.2 }}>
          {label}
        </span>
        {Icon ? <Icon size={16} strokeWidth={1.75} color={iconColor} aria-hidden /> : null}
      </div>

      <div
        style={{
          marginTop: 8,
          fontSize: token.fontSizeHeading3,
          fontWeight: 600,
          color: token.colorTextHeading,
          fontVariantNumeric: 'tabular-nums',
          lineHeight: 1.2,
        }}
      >
        {value}
      </div>

      {delta ? (
        <div
          style={{
            marginTop: 6,
            display: 'flex',
            alignItems: 'center',
            gap: 4,
            fontSize: 12,
          }}
        >
          {TrendIcon ? (
            <TrendIcon size={14} strokeWidth={2} color={trendColor} aria-hidden />
          ) : null}
          <span style={{ color: token.colorTextSecondary }}>{delta}</span>
        </div>
      ) : null}
    </Card>
  );
}
