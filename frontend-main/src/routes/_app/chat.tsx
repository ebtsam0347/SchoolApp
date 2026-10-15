// /chat — layout. Two-pane: left = conversation list, right = selected
// conversation (Outlet). Selection lives in the URL via /chat/$id.
//
// Permissions: every authed user can see /chat. Broadcast and admin
// sub-routes have their own beforeLoad guards.

import { createFileRoute, Outlet, useParams } from '@tanstack/react-router';
import { theme as antdTheme } from 'antd';
import { useState } from 'react';
import { ConversationList, NewGroupDrawer, StartDmDrawer } from '@/chat/conversations';

const { useToken } = antdTheme;

export const Route = createFileRoute('/_app/chat')({
  component: ChatLayout,
});

function ChatLayout() {
  const { token } = useToken();
  const params = useParams({ strict: false }) as { id?: string };
  const selectedId = params.id ? Number(params.id) : undefined;
  const [dmOpen, setDmOpen] = useState(false);
  const [groupOpen, setGroupOpen] = useState(false);

  return (
    <div
      style={{
        display: 'grid',
        gridTemplateColumns: 'minmax(280px, 320px) 1fr',
        height: 'calc(100dvh - 56px)',
        background: token.colorBgLayout,
        // Reserve space underneath the AppShell topbar — 56px matches the
        // shell's Topbar height.
      }}
    >
      <ConversationList
        selectedId={selectedId}
        onStartDm={() => setDmOpen(true)}
        onStartGroup={() => setGroupOpen(true)}
      />
      <div style={{ display: 'flex', flexDirection: 'column', minHeight: 0 }}>
        <Outlet />
      </div>
      <StartDmDrawer open={dmOpen} onClose={() => setDmOpen(false)} />
      <NewGroupDrawer open={groupOpen} onClose={() => setGroupOpen(false)} />
    </div>
  );
}
