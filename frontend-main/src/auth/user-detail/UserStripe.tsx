// Hero card at the top of /admin/users/$id. Avatar + display name +
// status tags on the left, last-sign-in stamp on the right. Pure
// presentational — no mutations live here.

import { Avatar, Tag, theme as antdTheme, Typography } from 'antd';
import { Lock, ShieldAlert, ShieldCheck } from 'lucide-react';
import { initialsOf, relTime, type UserDto } from '@/auth';

const { Text } = Typography;
const { useToken } = antdTheme;

export function UserStripe({ user }: { user: UserDto }) {
  const { token } = useToken();
  return (
    <header
      style={{
        display: 'grid',
        gridTemplateColumns: 'auto minmax(0, 1fr) auto',
        alignItems: 'center',
        gap: 20,
        padding: '20px 24px',
        background: token.colorBgContainer,
        border: `1px solid ${token.colorBorderSecondary}`,
        borderRadius: 'var(--app-radius-lg)',
      }}
    >
      <Avatar
        size={60}
        style={{
          background: token.colorPrimary,
          color: token.colorBgContainer,
          fontSize: 22,
          fontWeight: 600,
          flexShrink: 0,
          boxShadow: `0 6px 16px color-mix(in srgb, ${token.colorPrimary} 25%, transparent)`,
        }}
      >
        {initialsOf(user.fullName || user.username)}
      </Avatar>

      <div style={{ minWidth: 0, display: 'flex', flexDirection: 'column', gap: 6 }}>
        <h1
          style={{
            margin: 0,
            fontSize: 22,
            fontWeight: 600,
            lineHeight: 1.2,
            color: token.colorText,
          }}
        >
          {user.fullName}
        </h1>
        <div style={{ display: 'flex', alignItems: 'center', gap: 8, flexWrap: 'wrap' }}>
          <Text
            type="secondary"
            style={{
              fontSize: 13,
              fontFamily: 'ui-monospace, SFMono-Regular, Menlo, monospace',
            }}
          >
            @{user.username}
          </Text>
          {user.status === 'ACTIVE' && (
            <Tag
              color="success"
              icon={<ShieldCheck size={11} strokeWidth={2} />}
              style={{ margin: 0 }}
            >
              Active
            </Tag>
          )}
          {user.status === 'LOCKED' && (
            <Tag color="warning" icon={<Lock size={11} strokeWidth={2} />} style={{ margin: 0 }}>
              Locked
            </Tag>
          )}
          {user.status === 'DISABLED' && (
            <Tag
              color="default"
              icon={<ShieldAlert size={11} strokeWidth={2} />}
              style={{ margin: 0 }}
            >
              Disabled
            </Tag>
          )}
          <Tag style={{ margin: 0 }}>
            {user.employeeLinked ? 'Employee‑linked' : 'Standalone'}
          </Tag>
          {user.mustChangePw && (
            <Tag color="orange" style={{ margin: 0 }}>
              Must change pw
            </Tag>
          )}
        </div>
      </div>

      <div
        style={{
          display: 'flex',
          flexDirection: 'column',
          gap: 4,
          alignItems: 'flex-end',
        }}
      >
        <Text
          type="secondary"
          style={{ fontSize: 11, letterSpacing: '0.06em', textTransform: 'uppercase' }}
        >
          Last sign-in
        </Text>
        <Text style={{ fontSize: 13, color: token.colorText }}>
          {user.lastLoginAt ? relTime(user.lastLoginAt) : 'Never'}
        </Text>
      </div>
    </header>
  );
}
