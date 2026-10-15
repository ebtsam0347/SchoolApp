import { Popover, theme as antdTheme, Typography } from 'antd';
import { useState } from 'react';
import { useTheme } from './useTheme';
import type { ThemeDefinition } from './tokens';

const { Text } = Typography;
const { useToken } = antdTheme;

export function ThemeSwitcher() {
  const { theme, setThemeId, available } = useTheme();
  const [open, setOpen] = useState(false);
  const { token } = useToken();

  const content = (
    <div style={{ width: 300, padding: 4, color: token.colorText }}>
      <Text
        type="secondary"
        style={{
          fontSize: 11,
          letterSpacing: '0.08em',
          textTransform: 'uppercase',
        }}
      >
        Theme
      </Text>
      <div style={{ display: 'flex', flexDirection: 'column', gap: 6, marginTop: 10 }}>
        {available.map((t) => (
          <ThemeRow
            key={t.id}
            theme={t}
            isActive={t.id === theme.id}
            onSelect={() => {
              setThemeId(t.id);
              setOpen(false);
            }}
          />
        ))}
      </div>
    </div>
  );

  return (
    <Popover
      content={content}
      trigger="click"
      open={open}
      onOpenChange={setOpen}
      placement="bottomRight"
      arrow={false}
    >
      <button
        type="button"
        aria-label="Switch theme"
        title="Switch theme"
        style={{
          display: 'inline-flex',
          alignItems: 'center',
          justifyContent: 'center',
          width: 36,
          height: 36,
          borderRadius: 8,
          border: 'none',
          background: 'transparent',
          color: 'var(--app-topbar-text)',
          cursor: 'pointer',
        }}
      >
        <Swatch theme={theme} size={20} />
      </button>
    </Popover>
  );
}

function ThemeRow({
  theme,
  isActive,
  onSelect,
}: {
  theme: ThemeDefinition;
  isActive: boolean;
  onSelect: () => void;
}) {
  const { token } = useToken();
  return (
    <button
      type="button"
      onClick={onSelect}
      style={{
        display: 'flex',
        alignItems: 'center',
        gap: 12,
        width: '100%',
        padding: '8px 10px',
        borderRadius: 8,
        border: '1px solid',
        borderColor: isActive ? token.colorPrimary : 'transparent',
        background: isActive ? token.colorFillTertiary : 'transparent',
        color: token.colorText,
        cursor: 'pointer',
        textAlign: 'left',
      }}
      onMouseEnter={(e) => {
        if (!isActive) e.currentTarget.style.background = token.colorFillQuaternary;
      }}
      onMouseLeave={(e) => {
        if (!isActive) e.currentTarget.style.background = 'transparent';
      }}
    >
      <Swatch theme={theme} size={28} />
      <div style={{ display: 'flex', flexDirection: 'column', minWidth: 0, flex: 1 }}>
        <span style={{ fontSize: 13, fontWeight: 500, color: token.colorText }}>{theme.label}</span>
        <span style={{ fontSize: 11, lineHeight: 1.3, color: token.colorTextSecondary }}>
          {theme.blurb}
        </span>
      </div>
    </button>
  );
}

function Swatch({ theme, size }: { theme: ThemeDefinition; size: number }) {
  // Three-stop preview: sidebar | brand | accent. Reads as "the theme at a glance".
  return (
    <div
      style={{
        width: size,
        height: size,
        borderRadius: '50%',
        flexShrink: 0,
        background: `conic-gradient(${theme.preview.sidebar} 0deg 120deg, ${theme.preview.brand} 120deg 240deg, ${theme.preview.accent} 240deg 360deg)`,
        boxShadow: 'inset 0 0 0 1px rgba(0,0,0,0.2)',
      }}
    />
  );
}
