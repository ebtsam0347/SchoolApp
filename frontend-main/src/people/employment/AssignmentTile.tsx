// One tile in the "Current assignment" strip on /people/$id/employment.
// Icon + uppercase label + value + optional code/code-secondary line.

import { Typography, theme as antdTheme } from 'antd';
import type { LucideIcon } from 'lucide-react';

const { Text } = Typography;
const { useToken } = antdTheme;

export type AssignmentTileProps = {
  icon: LucideIcon;
  label: string;
  value?: string | null;
  secondary?: string | null;
};

export function AssignmentTile({
  icon: Icon,
  label,
  value,
  secondary,
}: AssignmentTileProps) {
  const { token } = useToken();
  return (
    <div
      style={{
        display: 'flex',
        alignItems: 'flex-start',
        gap: 12,
        padding: 12,
        background: token.colorFillQuaternary,
        borderRadius: 'var(--app-radius-md)',
      }}
    >
      <span
        style={{
          width: 32,
          height: 32,
          borderRadius: 'var(--app-radius-sm)',
          background: `color-mix(in srgb, ${token.colorPrimary} 14%, transparent)`,
          color: token.colorPrimary,
          display: 'inline-flex',
          alignItems: 'center',
          justifyContent: 'center',
          flexShrink: 0,
        }}
      >
        <Icon size={16} strokeWidth={1.75} />
      </span>
      <div style={{ minWidth: 0 }}>
        <div
          style={{
            fontSize: 11,
            color: token.colorTextSecondary,
            textTransform: 'uppercase',
            letterSpacing: '0.06em',
          }}
        >
          {label}
        </div>
        <div
          style={{
            fontSize: 14,
            fontWeight: 600,
            color: token.colorTextHeading,
          }}
        >
          {value ?? <Text type="secondary">—</Text>}
        </div>
        {secondary ? (
          <Text type="secondary" style={{ fontSize: 12 }}>
            {secondary}
          </Text>
        ) : null}
      </div>
    </div>
  );
}
