// Explicit "this route isn't wired yet" panel.
//
// Two of the canteen surfaces (devices, roster) are scheduled for a later
// backend phase, and the meal ledger lands with them. Rather than let a
// 404 render as a generic red error — which reads like a bug and sends
// people hunting — we detect the missing route and say plainly what is
// expected and what still works today.
//
// Delete the call sites as each endpoint ships; nothing else depends on it.

import { Card, Typography, theme as antdTheme } from 'antd';
import type { ReactNode } from 'react';
import { PlugZap } from 'lucide-react';

const { Text, Paragraph } = Typography;
const { useToken } = antdTheme;

export type PendingBackendNoticeProps = {
  /** Human name of the missing surface, e.g. "canteen device list". */
  what: string;
  /** The endpoint this page will call, e.g. "GET /canteen/devices". */
  endpoint: string;
  /** What the operator can do in the meantime, if anything. */
  children?: ReactNode;
};

export function PendingBackendNotice({
  what,
  endpoint,
  children,
}: PendingBackendNoticeProps) {
  const { token } = useToken();
  return (
    <Card
      styles={{ body: { padding: 28, textAlign: 'center' } }}
      style={{ borderStyle: 'dashed' }}
    >
      <span
        aria-hidden="true"
        style={{
          width: 44,
          height: 44,
          borderRadius: 12,
          display: 'inline-flex',
          alignItems: 'center',
          justifyContent: 'center',
          background: `color-mix(in srgb, ${token.colorWarning} 14%, transparent)`,
          color: token.colorWarning,
          marginBottom: 12,
        }}
      >
        <PlugZap size={22} strokeWidth={1.75} />
      </span>
      <div style={{ fontSize: 15, fontWeight: 600, marginBottom: 6 }}>
        The {what} API is not live yet
      </div>
      <Paragraph type="secondary" style={{ fontSize: 13, maxWidth: 520, margin: '0 auto' }}>
        This screen is built against{' '}
        <Text code style={{ fontSize: 12 }}>
          {endpoint}
        </Text>
        , which the backend delivers in the next phase. The page will start
        filling in on its own the moment that route responds — no frontend
        change needed.
      </Paragraph>
      {children ? (
        <div style={{ marginTop: 14, fontSize: 13 }}>{children}</div>
      ) : null}
    </Card>
  );
}
