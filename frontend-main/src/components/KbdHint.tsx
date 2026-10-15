import { theme as antdTheme } from 'antd';
import type { CSSProperties } from 'react';

const { useToken } = antdTheme;

export type KbdHintProps = {
  /** Single key string, or an array of keys to render as separate chips. */
  keys: string | string[];
  size?: 'sm' | 'md';
  className?: string;
  style?: CSSProperties;
};

export function KbdHint({ keys, size = 'sm', className, style }: KbdHintProps) {
  const { token } = useToken();
  const list = Array.isArray(keys) ? keys : [keys];

  const dims =
    size === 'md'
      ? { fontSize: 12, padX: 6, padY: 2, minWidth: 22, gap: 3 }
      : { fontSize: 11, padX: 5, padY: 1, minWidth: 18, gap: 2 };

  return (
    <span
      className={className}
      style={{
        display: 'inline-flex',
        alignItems: 'center',
        gap: dims.gap,
        ...style,
      }}
    >
      {list.map((key, i) => (
        <kbd
          key={`${key}-${i}`}
          style={{
            display: 'inline-flex',
            alignItems: 'center',
            justifyContent: 'center',
            minWidth: dims.minWidth,
            padding: `${dims.padY}px ${dims.padX}px`,
            fontFamily: token.fontFamily,
            fontSize: dims.fontSize,
            fontWeight: 500,
            lineHeight: 1.2,
            color: token.colorTextSecondary,
            background: token.colorFillQuaternary,
            border: `1px solid ${token.colorBorderSecondary}`,
            borderRadius: token.borderRadiusSM,
          }}
        >
          {key}
        </kbd>
      ))}
    </span>
  );
}
