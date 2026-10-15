// /chat/admin — HR/ADMIN compliance viewer. Gated on chat.admin.read.

import { createFileRoute, redirect } from '@tanstack/react-router';
import { theme as antdTheme } from 'antd';
import { buildAbility } from '@/auth';
import { readSnapshot } from '@/lib';
import { canAdminRead } from '@/chat';
import { AdminConversationViewer } from '@/chat/admin';
import { PageHeader } from '@/components';

const { useToken } = antdTheme;

export const Route = createFileRoute('/_app/chat/admin')({
  beforeLoad: () => {
    const snap = readSnapshot();
    if (!snap?.user) return;
    if (!canAdminRead(buildAbility(snap.user))) {
      throw redirect({ to: '/chat' });
    }
  },
  component: AdminRoute,
});

function AdminRoute() {
  const { token } = useToken();
  return (
    <div
      style={{
        display: 'flex',
        flexDirection: 'column',
        gap: 16,
        padding: 24,
        height: '100%',
        background: token.colorBgLayout,
        minHeight: 0,
      }}
    >
      <PageHeader
        eyebrow="Chat"
        title="Admin viewer"
        description="Read any user's conversations. Hard-deletes are audit-emitting."
      />
      <div style={{ flex: 1, minHeight: 0 }}>
        <AdminConversationViewer />
      </div>
    </div>
  );
}
