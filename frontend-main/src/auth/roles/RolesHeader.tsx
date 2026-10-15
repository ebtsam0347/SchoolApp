// /admin/roles header — eyebrow, title, count chip, intro copy, primary
// CTA. Lives separately because the route file would otherwise spend 80
// LOC on chrome before the first KPI renders.

import { Button, Space, theme as antdTheme, Typography } from 'antd';
import { Plus } from 'lucide-react';

const { Text } = Typography;
const { useToken } = antdTheme;

export type RolesHeaderProps = {
  totalRoles: number;
  totalPermissions: number;
  onCreate: () => void;
};

export function RolesHeader({
  totalRoles,
  totalPermissions,
  onCreate,
}: RolesHeaderProps) {
  const { token } = useToken();
  return (
    <header
      style={{
        display: 'grid',
        gridTemplateColumns: 'minmax(0, 1fr) auto',
        alignItems: 'end',
        gap: 'var(--app-space-lg)',
        paddingBottom: 20,
        marginBottom: 4,
        borderBottom: `1px solid ${token.colorBorderSecondary}`,
      }}
    >
      <div style={{ minWidth: 0 }}>
        <Text
          type="secondary"
          style={{
            display: 'block',
            fontSize: 11,
            fontWeight: 500,
            letterSpacing: '0.08em',
            textTransform: 'uppercase',
            color: token.colorTextTertiary,
            marginBottom: 6,
          }}
        >
          Identity & Access
        </Text>
        <div
          style={{
            display: 'flex',
            alignItems: 'baseline',
            gap: 14,
            flexWrap: 'wrap',
          }}
        >
          <h1
            style={{
              margin: 0,
              fontSize: 28,
              fontWeight: 600,
              lineHeight: 1.15,
              letterSpacing: '-0.01em',
              color: token.colorText,
            }}
          >
            Roles & Permissions
          </h1>
          <span
            style={{
              display: 'inline-flex',
              alignItems: 'center',
              gap: 6,
              padding: '2px 10px',
              borderRadius: 'var(--app-radius-full)',
              fontSize: 12,
              fontWeight: 500,
              color: token.colorTextSecondary,
              background: token.colorFillQuaternary,
            }}
          >
            {totalRoles} role{totalRoles === 1 ? '' : 's'} · {totalPermissions} permission
            {totalPermissions === 1 ? '' : 's'}
          </span>
        </div>
        <Text
          type="secondary"
          style={{
            display: 'block',
            marginTop: 6,
            fontSize: 13,
            maxWidth: 640,
            lineHeight: 1.55,
          }}
        >
          Roles bundle permissions, and users get features by holding roles.
          Attach roles from a user's detail page.
        </Text>
      </div>
      <Space size={8} style={{ alignSelf: 'end' }}>
        <Button
          type="primary"
          icon={<Plus size={14} strokeWidth={1.75} />}
          onClick={onCreate}
        >
          New role
        </Button>
      </Space>
    </header>
  );
}
