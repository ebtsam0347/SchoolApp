import { theme as antdTheme } from 'antd';

import { parseDateOnly } from '../format';

const { useToken } = antdTheme;

export function DateBadge({ iso }: { iso: string }) {
  const { token } = useToken();
  const d = parseDateOnly(iso)!;
  const month = d
    .toLocaleDateString('en-GB', { month: 'short' })
    .toUpperCase();

  return (
    <div
      aria-hidden
      style={{
        flexShrink: 0,
        width: 48,
        height: 56,
        borderRadius: 10,
        border: `1px solid ${token.colorBorderSecondary}`,
        background: token.colorFillQuaternary,
        display: 'flex',
        flexDirection: 'column',
        alignItems: 'center',
        justifyContent: 'center',
        overflow: 'hidden',
      }}
    >
      <span
        style={{
          fontSize: 9,
          fontWeight: 700,
          letterSpacing: '0.08em',
          color: token.colorTextTertiary,
          padding: '2px 0',
          width: '100%',
          textAlign: 'center',
          background: token.colorBgContainer,
          borderBottom: `1px solid ${token.colorBorderSecondary}`,
        }}
      >
        {month}
      </span>
      <span
        style={{
          fontSize: 18,
          fontWeight: 700,
          color: token.colorText,
          lineHeight: 1,
          marginTop: 4,
        }}
      >
        {d.getDate()}
      </span>
    </div>
  );
}
