// /chat/broadcast — HR/ADMIN composer for the org-wide channel. Gated on
// chat.broadcast.write; non-permitted users are redirected back to /chat.

import { createFileRoute, redirect } from '@tanstack/react-router';
import { theme as antdTheme } from 'antd';
import { buildAbility } from '@/auth';
import { readSnapshot } from '@/lib';
import { canBroadcast } from '@/chat';
import { BroadcastComposer } from '@/chat/broadcast';
import { PageHeader } from '@/components';

const { useToken } = antdTheme;

export const Route = createFileRoute('/_app/chat/broadcast')({
  beforeLoad: () => {
    const snap = readSnapshot();
    if (!snap?.user) return;
    if (!canBroadcast(buildAbility(snap.user))) {
      throw redirect({ to: '/chat' });
    }
  },
  component: BroadcastRoute,
});

function BroadcastRoute() {
  const { token } = useToken();
  return (
    <div
      style={{
        padding: 24,
        height: '100%',
        overflowY: 'auto',
        background: token.colorBgLayout,
      }}
    >
      <PageHeader
        eyebrow="Chat"
        title="Send announcement"
        description="Posts to every employee's announcements channel."
      />
      <div style={{ marginTop: 16, maxWidth: 720 }}>
        <BroadcastComposer />
      </div>
    </div>
  );
}
