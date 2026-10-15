// Left pane of the chat surface — list of conversations the user belongs
// to. Driven by GET /chat/conversations; row click navigates to
// `/chat/$id` (URL is the source of truth for selection).

import { useQuery } from '@tanstack/react-query';
import { useNavigate } from '@tanstack/react-router';
import { Button, Dropdown, Spin, theme as antdTheme } from 'antd';
import { MessageSquarePlus, Megaphone, ShieldCheck, Users } from 'lucide-react';
import { useContext, useEffect } from 'react';
import { AbilityContext, useAuth } from '@/auth';
import { EmptyState, RequestState } from '@/components';
import { chatBff } from '../bff-api';
import { chatKeys } from '../query-keys';
import { canAdminRead, canBroadcast } from '../permissions';
import { useChatPresence } from '../presence-store';
import { seedFromResolved } from '../user-resolver';
import type { PresenceStatus } from '../types';
import { ConversationRow } from './ConversationRow';

const { useToken } = antdTheme;

export type ConversationListProps = {
  /** Currently selected conversation id (from the URL). */
  selectedId?: number;
  onStartDm: () => void;
  onStartGroup: () => void;
};

export function ConversationList({ selectedId, onStartDm, onStartGroup }: ConversationListProps) {
  const { token } = useToken();
  const ability = useContext(AbilityContext);
  const auth = useAuth();
  const navigate = useNavigate();

  // /bff/chat/inbox returns conversations + resolved member identities +
  // presence snapshot in one round-trip — see CHAT_WISHLIST §3.
  const query = useQuery({
    queryKey: chatKeys.conversations,
    queryFn: ({ signal }) => chatBff.inbox(signal),
    staleTime: 30_000,
  });

  // Seed the user-resolver cache and the presence store from the inbox
  // payload so member names + dots render on first paint without an
  // extra round-trip per conversation.
  useEffect(() => {
    if (!query.data) return;
    seedFromResolved(query.data.resolved);
    useChatPresence.getState().seedPresence(
      query.data.presence.map((p) => ({
        userId: p.userId,
        status: (p.status === 'online' ? 'online' : 'offline') as PresenceStatus,
        lastSeenAt: p.lastSeenAt,
      })),
    );
  }, [query.data]);

  const conversations = query.data?.conversations ?? [];

  const newMenuItems = [
    { key: 'dm', icon: <MessageSquarePlus size={14} />, label: 'New direct message', onClick: onStartDm },
    { key: 'group', icon: <Users size={14} />, label: 'New group', onClick: onStartGroup },
  ];

  return (
    <div
      style={{
        display: 'flex',
        flexDirection: 'column',
        height: '100%',
        background: token.colorBgContainer,
        borderRight: `1px solid ${token.colorBorderSecondary}`,
      }}
    >
      <div
        style={{
          display: 'flex',
          alignItems: 'center',
          justifyContent: 'space-between',
          padding: '12px 16px',
          borderBottom: `1px solid ${token.colorBorderSecondary}`,
        }}
      >
        <strong>Chats</strong>
        <Dropdown menu={{ items: newMenuItems }} trigger={['click']} placement="bottomRight">
          <Button size="small" type="primary" icon={<MessageSquarePlus size={14} />}>
            New
          </Button>
        </Dropdown>
      </div>

      <div style={{ flex: 1, overflowY: 'auto', minHeight: 0 }}>
        <RequestState
          loading={query.isLoading}
          error={query.error}
          empty={conversations.length === 0}
          loadingFallback={
            <div style={{ display: 'flex', justifyContent: 'center', padding: 24 }}>
              <Spin />
            </div>
          }
          emptyFallback={
            <EmptyState
              size="sm"
              icon={MessageSquarePlus}
              title="No conversations yet"
              description="Start a direct message to get started."
              action={
                <Button type="primary" onClick={onStartDm}>
                  Start a chat
                </Button>
              }
            />
          }
          errorTitle="Couldn't load conversations"
        >
          <ul style={{ listStyle: 'none', margin: 0, padding: 0 }}>
            {conversations.map((c) => (
              <ConversationRow
                key={c.id}
                conversation={c}
                selected={selectedId === c.id}
                selfUserId={auth.user?.userId ?? null}
                onSelect={() => navigate({ to: '/chat/$id', params: { id: String(c.id) } })}
              />
            ))}
          </ul>
        </RequestState>
      </div>

      {(canBroadcast(ability) || canAdminRead(ability)) && (
        <div
          style={{
            display: 'flex',
            flexDirection: 'column',
            gap: 4,
            padding: 12,
            borderTop: `1px solid ${token.colorBorderSecondary}`,
          }}
        >
          {canBroadcast(ability) && (
            <Button
              size="small"
              icon={<Megaphone size={14} />}
              onClick={() => navigate({ to: '/chat/broadcast' })}
            >
              Send announcement
            </Button>
          )}
          {canAdminRead(ability) && (
            <Button
              size="small"
              icon={<ShieldCheck size={14} />}
              onClick={() => navigate({ to: '/chat/admin' })}
            >
              Admin viewer
            </Button>
          )}
        </div>
      )}

    </div>
  );
}
