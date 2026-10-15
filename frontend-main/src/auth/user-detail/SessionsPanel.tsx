// Sessions panel — refresh-token rows for the user. Active sessions get
// a Revoke button (with confirm); revoked sessions show as compact dim
// rows underneath as a recent-activity strip.

import {
  App,
  Button,
  Empty,
  List,
  Space,
  Tag,
  theme as antdTheme,
} from 'antd';
import { useMutation, useQueryClient } from '@tanstack/react-query';
import { Clock3, Globe, LogOut } from 'lucide-react';
import { Section } from '@/components';
import {
  adminSessionsApi,
  describeDevice,
  isSessionExpired,
  relTime,
  type SessionDto,
} from '@/auth';
import { deviceFamilyIcon, formatBrowser, formatOs } from '@/lib/user-agent';
import { getErrorMessage } from '@/lib';
import { overviewKey } from './query-keys';

const { useToken } = antdTheme;

export type SessionsPanelProps = {
  userId: number;
  sessions: SessionDto[];
};

export function SessionsPanel({ userId, sessions }: SessionsPanelProps) {
  const { token } = useToken();
  const { message, modal } = App.useApp();
  const queryClient = useQueryClient();

  const revokeMutation = useMutation({
    mutationFn: (id: number) => adminSessionsApi.revoke(id),
    onSuccess: (revoked) => {
      void queryClient.invalidateQueries({ queryKey: overviewKey(userId) });
      const stamp = new Date().toLocaleTimeString(undefined, {
        hour: 'numeric',
        minute: '2-digit',
      });
      message.success(`Revoked ${describeDevice(revoked)} at ${stamp}.`);
    },
    onError: (e) => message.error(getErrorMessage(e)),
  });

  const active = sessions.filter((s) => !s.revokedAt && !isSessionExpired(s));
  const revoked = sessions
    .filter((s) => s.revokedAt)
    .sort((a, b) => (a.revokedAt! < b.revokedAt! ? 1 : -1));

  return (
    <Section title="Sessions">
      {active.length === 0 ? (
        <Empty image={Empty.PRESENTED_IMAGE_SIMPLE} description="No active sessions." />
      ) : (
        <List
          dataSource={active}
          renderItem={(s) => (
            <List.Item
              actions={[
                <Button
                  key="revoke"
                  danger
                  size="small"
                  icon={<LogOut size={13} strokeWidth={1.75} />}
                  loading={revokeMutation.isPending && revokeMutation.variables === s.id}
                  onClick={() =>
                    modal.confirm({
                      title: `Revoke "${describeDevice(s)}"?`,
                      content: 'The user will need to sign in again.',
                      okText: 'Revoke',
                      okButtonProps: { danger: true },
                      onOk: () => revokeMutation.mutateAsync(s.id),
                    })
                  }
                >
                  Revoke
                </Button>,
              ]}
            >
              <List.Item.Meta
                avatar={<DeviceIcon session={s} />}
                title={
                  <span style={{ display: 'inline-flex', alignItems: 'center', gap: 8 }}>
                    <span style={{ color: token.colorText, fontSize: 13, fontWeight: 600 }}>
                      {s.deviceLabel || 'Unknown device'}
                    </span>
                    {(formatBrowser(s.browser) || formatOs(s.os)) && (
                      <Tag style={{ margin: 0, fontSize: 11 }}>
                        {[formatBrowser(s.browser), formatOs(s.os)].filter(Boolean).join(' · ')}
                      </Tag>
                    )}
                  </span>
                }
                description={
                  <Space wrap size={[16, 4]} style={{ fontSize: 12 }}>
                    <span>
                      <Globe size={11} style={{ verticalAlign: -1, marginRight: 4 }} />
                      <code>{s.ipAddress || '—'}</code>
                    </span>
                    <span>
                      <Clock3 size={11} style={{ verticalAlign: -1, marginRight: 4 }} />
                      Last seen {relTime(s.lastSeenAt)}
                    </span>
                    <span>Issued {new Date(s.issuedAt).toLocaleString()}</span>
                    <span>Expires {new Date(s.expiresAt).toLocaleDateString()}</span>
                  </Space>
                }
              />
            </List.Item>
          )}
        />
      )}

      {revoked.length > 0 && (
        <div style={{ marginTop: 'var(--app-space-md)' }}>
          <div
            style={{
              fontSize: 11,
              fontWeight: 600,
              letterSpacing: '0.06em',
              textTransform: 'uppercase',
              color: token.colorTextTertiary,
              marginBottom: 'var(--app-space-sm)',
            }}
          >
            Recently revoked
          </div>
          <div style={{ display: 'flex', flexDirection: 'column', gap: 6 }}>
            {revoked.map((s) => (
              <div
                key={s.id}
                style={{
                  display: 'grid',
                  gridTemplateColumns: 'minmax(0, 1fr) auto',
                  gap: 12,
                  alignItems: 'center',
                  padding: '8px 12px',
                  borderRadius: 'var(--app-radius-sm)',
                  background: token.colorFillQuaternary,
                  border: `1px solid ${token.colorBorderSecondary}`,
                  opacity: 0.85,
                }}
              >
                <div
                  style={{ minWidth: 0, fontSize: 12, color: token.colorTextSecondary }}
                >
                  <span style={{ color: token.colorText, fontWeight: 500 }}>
                    {s.deviceLabel || 'Unknown device'}
                  </span>{' '}
                  · revoked {s.revokedAt ? relTime(s.revokedAt) : '—'}
                  {s.revokedBy ? ` by ${s.revokedBy}` : ''}
                </div>
                <Tag style={{ margin: 0, fontSize: 11 }}>Revoked</Tag>
              </div>
            ))}
          </div>
        </div>
      )}
    </Section>
  );
}

function DeviceIcon({ session }: { session: SessionDto }) {
  const { token } = useToken();
  const Icon = deviceFamilyIcon(session.deviceFamily);
  return (
    <span
      aria-hidden="true"
      style={{
        width: 36,
        height: 36,
        flexShrink: 0,
        borderRadius: 'var(--app-radius-sm)',
        display: 'inline-flex',
        alignItems: 'center',
        justifyContent: 'center',
        background: `color-mix(in srgb, ${token.colorPrimary} 10%, transparent)`,
        color: token.colorPrimary,
      }}
    >
      <Icon size={18} strokeWidth={1.75} />
    </span>
  );
}
