import {
  Alert,
  Button,
  Drawer,
  theme as antdTheme,
  Typography,
} from 'antd';
import { useQuery } from '@tanstack/react-query';
import { ExternalLink } from 'lucide-react';

import { JsonViewer } from '@/components';
import { getErrorMessage } from '@/lib';
import { auditApi } from '../api';

const { Text } = Typography;
const { useToken } = antdTheme;

export type EventDetailDrawerProps = {
  eventId: number | null;
  onClose: () => void;
  onViewByCorrelation: (correlationId: string) => void;
};

export function EventDetailDrawer({
  eventId,
  onClose,
  onViewByCorrelation,
}: EventDetailDrawerProps) {
  const { token } = useToken();

  const eventQuery = useQuery({
    queryKey: ['admin', 'audit', 'event', eventId],
    enabled: eventId !== null,
    queryFn: ({ signal }) => auditApi.event(eventId!, signal),
  });

  const event = eventQuery.data ?? null;

  return (
    <Drawer
      open={eventId !== null}
      title={event ? <span>Audit event #{event.id}</span> : 'Audit event'}
      size={600}
      onClose={onClose}
      destroyOnHidden
      loading={eventQuery.isLoading}
    >
      {eventQuery.isError ? (
        <Alert
          type="error"
          showIcon
          title="Couldn't load event"
          description={getErrorMessage(eventQuery.error)}
        />
      ) : (
        event && (
          <div style={{ display: 'flex', flexDirection: 'column', gap: 16 }}>
            <div>
              <Text
                type="secondary"
                style={{
                  fontSize: 11,
                  textTransform: 'uppercase',
                  letterSpacing: '0.06em',
                }}
              >
                Headline
              </Text>
              <div
                style={{ fontSize: 14, color: token.colorText, marginTop: 4 }}
              >
                {event.message ?? '—'}
              </div>
            </div>

            <div
              style={{
                display: 'grid',
                gridTemplateColumns: '120px 1fr',
                rowGap: 8,
                columnGap: 12,
                fontSize: 12,
              }}
            >
              <KV k="Event id" v={<code>{event.eventId}</code>} />
              <KV k="Source" v={event.sourceService} />
              <KV k="Action" v={<code>{event.action}</code>} />
              <KV
                k="Entity"
                v={
                  <code>
                    {event.entityType} / {event.entityId}
                  </code>
                }
              />
              <KV
                k="Actor"
                v={
                  event.actorLabel
                    ? `${event.actorLabel} (@${event.actorUsername})`
                    : 'system'
                }
              />
              <KV k="IP" v={<code>{event.ipAddress ?? '—'}</code>} />
              <KV k="Correlation" v={<code>{event.correlationId}</code>} />
              <KV k="Occurred" v={new Date(event.occurredAt).toLocaleString()} />
              <KV k="Ingested" v={new Date(event.ingestedAt).toLocaleString()} />
              <KV k="User-visible" v={event.userVisible ? 'Yes' : 'No'} />
            </div>

            <JsonSection title="Changes" value={event.changes} />
            <JsonSection title="Before" value={event.before} />
            <JsonSection title="After" value={event.after} />
            <JsonSection title="Metadata" value={event.metadata} />

            <Button
              icon={<ExternalLink size={14} strokeWidth={1.75} />}
              onClick={() => onViewByCorrelation(event.correlationId)}
            >
              View by correlation
            </Button>
          </div>
        )
      )}
    </Drawer>
  );
}

function KV({ k, v }: { k: string; v: React.ReactNode }) {
  const { token } = useToken();
  return (
    <>
      <span
        style={{
          color: token.colorTextTertiary,
          fontSize: 11,
          textTransform: 'uppercase',
          letterSpacing: '0.04em',
        }}
      >
        {k}
      </span>
      <span style={{ color: token.colorText, fontSize: 12 }}>{v}</span>
    </>
  );
}

function JsonSection({ title, value }: { title: string; value: unknown }) {
  if (value === null || value === undefined) return null;
  const json = JSON.stringify(value, null, 2);
  if (json === '{}' || json === '[]' || json === 'null') return null;

  return (
    <div>
      <Text
        type="secondary"
        style={{
          fontSize: 11,
          textTransform: 'uppercase',
          letterSpacing: '0.06em',
          display: 'block',
          marginBottom: 6,
        }}
      >
        {title}
      </Text>
      <JsonViewer value={value} maxHeight={240} />
    </div>
  );
}
