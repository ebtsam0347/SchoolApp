// HR / ADMIN-only composer for the org-wide announcement channel.
// Wraps the regular Composer in `broadcast` mode — same UX, different
// endpoint (`/chat/broadcast/messages`). Perm gating happens at the route.

import { Alert, theme as antdTheme } from 'antd';
import { Composer } from '../composer';

const { useToken } = antdTheme;

export function BroadcastComposer() {
  const { token } = useToken();
  return (
    <div
      style={{
        display: 'flex',
        flexDirection: 'column',
        gap: 16,
        background: token.colorBgContainer,
        padding: 16,
        borderRadius: token.borderRadiusLG,
      }}
    >
      <Alert
        type="info"
        showIcon
        title="Send to everyone"
        description="This message lands in every employee's announcements channel and adds a bell entry for the people you @-mention."
      />
      {/* The Composer treats `conversationId` as opaque when broadcast=true. */}
      <Composer conversationId={0} broadcast placeholder="Write an announcement…" />
    </div>
  );
}
