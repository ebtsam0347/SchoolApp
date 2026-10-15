// /admin/users page header. Eyebrow + title + count chip + intro copy +
// Refresh / New user buttons.

import { Button, Space, Tooltip, theme as antdTheme, Typography } from 'antd';
import { RefreshCcw, UserPlus } from 'lucide-react';

const { Text } = Typography;
const { useToken } = antdTheme;

export type UsersHeaderProps = {
  totalElements: number | undefined;
  fetching: boolean;
  onRefresh: () => void;
  onCreate: () => void;
};

export function UsersHeader({
  totalElements,
  fetching,
  onRefresh,
  onCreate,
}: UsersHeaderProps) {
  const { token } = useToken();
  return (
    <header
      style={{
        display: 'grid',
        gridTemplateColumns: 'minmax(0, 1fr) auto',
        alignItems: 'end',
        gap: 'var(--app-space-lg)',
        paddingBottom: 20,
        marginBottom: 20,
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
          style={{ display: 'flex', alignItems: 'baseline', gap: 14, flexWrap: 'wrap' }}
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
            Users
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
            {typeof totalElements === 'number'
              ? `${totalElements.toLocaleString()} total`
              : '…'}
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
          Create users, reset passwords, revoke sessions, and review login history.
        </Text>
      </div>
      <Space size={8} style={{ alignSelf: 'end' }}>
        <Tooltip title="Refresh">
          <Button
            icon={<RefreshCcw size={14} strokeWidth={1.75} />}
            onClick={onRefresh}
            loading={fetching}
            aria-label="Refresh"
          />
        </Tooltip>
        <Button
          type="primary"
          icon={<UserPlus size={14} strokeWidth={1.75} />}
          onClick={onCreate}
        >
          New user
        </Button>
      </Space>
    </header>
  );
}
