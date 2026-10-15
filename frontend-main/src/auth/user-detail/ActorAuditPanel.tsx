// "Recent actions" — audit events keyed to this user as the actor. Links
// out to the audit explorer pre-filtered by actorUserId for the full
// drill-down experience.

import { Empty, Tag, theme as antdTheme } from 'antd';
import { Link } from '@tanstack/react-router';
import { Section } from '@/components';
import { relTime } from '@/auth';
import type { AuditEventDto } from '@/audit/api';

const { useToken } = antdTheme;

export type ActorAuditPanelProps = {
  userId: number;
  actions: AuditEventDto[];
};

export function ActorAuditPanel({ userId, actions }: ActorAuditPanelProps) {
  const { token } = useToken();

  return (
    <Section
      title="Recent actions"
      actions={
        <Link
          to="/admin/audit"
          search={{ actorUserId: userId } as never}
          style={{ fontSize: 13, color: token.colorPrimary }}
        >
          See all →
        </Link>
      }
    >
      {actions.length === 0 ? (
        <Empty image={Empty.PRESENTED_IMAGE_SIMPLE} description="No actions recorded yet." />
      ) : (
        <ul
          style={{
            listStyle: 'none',
            margin: 0,
            padding: 0,
            display: 'flex',
            flexDirection: 'column',
            gap: 6,
            maxHeight: 360,
            overflowY: 'auto',
            paddingRight: 4,
          }}
        >
          {actions.map((evt) => (
            <li
              key={evt.id}
              style={{
                display: 'grid',
                gridTemplateColumns: 'minmax(0, 1fr) auto',
                gap: 'var(--app-space-sm)',
                padding: '8px 12px',
                borderRadius: 'var(--app-radius-sm)',
                background: token.colorFillQuaternary,
                alignItems: 'center',
              }}
            >
              <div style={{ minWidth: 0 }}>
                <div
                  style={{
                    fontSize: 13,
                    color: token.colorText,
                    overflow: 'hidden',
                    textOverflow: 'ellipsis',
                    whiteSpace: 'nowrap',
                  }}
                >
                  {evt.message ?? `${evt.entityType} ${evt.entityId}`}
                </div>
                <div
                  style={{
                    display: 'flex',
                    gap: 'var(--app-space-sm)',
                    alignItems: 'center',
                    fontSize: 11,
                    color: token.colorTextTertiary,
                    marginTop: 2,
                  }}
                >
                  <Tag style={{ margin: 0, fontSize: 10, lineHeight: 1.4 }}>
                    {evt.sourceService}
                  </Tag>
                  <code style={{ fontSize: 11 }}>{evt.action}</code>
                </div>
              </div>
              <span
                style={{
                  fontSize: 11,
                  color: token.colorTextTertiary,
                  whiteSpace: 'nowrap',
                }}
              >
                {relTime(evt.occurredAt)}
              </span>
            </li>
          ))}
        </ul>
      )}
    </Section>
  );
}
