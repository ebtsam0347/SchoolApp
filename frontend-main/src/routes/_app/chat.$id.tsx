// /chat/$id — selected conversation. Header (title + member count + search
// affordance), message timeline, composer.

import { createFileRoute } from '@tanstack/react-router';
import { useQuery } from '@tanstack/react-query';
import { Avatar, Button, theme as antdTheme, Typography } from 'antd';
import { Megaphone, Search, Users } from 'lucide-react';
import { useState } from 'react';
import { useAuth } from '@/auth';
import { conversationsApi, chatKeys, conversationDisplayTitle } from '@/chat';
import { Composer } from '@/chat/composer';
import { MessageTimeline } from '@/chat/messages';
import { LastSeenLabel } from '@/chat/presence';
import { SearchPanel } from '@/chat/search/SearchPanel';
import { useUserName } from '@/chat/user-resolver';
import { UserAvatar } from '@/chat/UserAvatar';

const { Title } = Typography;
const { useToken } = antdTheme;

const HEADER_AVATAR_SIZE = 44;

export const Route = createFileRoute('/_app/chat/$id')({
  component: ChatConversationRoute,
});

function ChatConversationRoute() {
  const { token } = useToken();
  const { id } = Route.useParams();
  const conversationId = Number(id);
  const auth = useAuth();
  const [searchOpen, setSearchOpen] = useState(false);

  const conversationQuery = useQuery({
    queryKey: chatKeys.conversation(conversationId),
    queryFn: ({ signal }) => conversationsApi.get(conversationId, signal),
    staleTime: 30_000,
  });

  const conversation = conversationQuery.data;
  const myUserId = auth.user?.userId;
  const peerUserId =
    conversation?.type === 'DM' && myUserId != null
      ? conversation.memberUserIds.find((u) => u !== myUserId) ?? null
      : null;
  const peerName = useUserName(peerUserId ?? undefined);
  const title = conversation
    ? conversationDisplayTitle(
        conversation.type,
        conversation.title,
        peerUserId != null ? peerName : null,
        `Conversation #${conversationId}`,
      )
    : `Conversation #${conversationId}`;

  return (
    <div style={{ display: 'flex', flexDirection: 'column', height: '100%', minHeight: 0 }}>
      <header
        style={{
          display: 'flex',
          alignItems: 'center',
          gap: 12,
          padding: '12px 16px',
          borderBottom: `1px solid ${token.colorBorderSecondary}`,
          background: token.colorBgContainer,
        }}
      >
        <ConversationAvatar
          type={conversation?.type ?? 'DM'}
          title={title}
          peerUserId={peerUserId}
        />
        <div style={{ flex: 1, minWidth: 0 }}>
          <Title level={5} style={{ margin: 0 }} ellipsis>
            {title}
          </Title>
          {conversation?.type === 'DM' && peerUserId != null && (
            <LastSeenLabel userId={peerUserId} />
          )}
          {conversation?.type === 'GROUP' && (
            <Typography.Text type="secondary" style={{ fontSize: 12 }}>
              {conversation.memberUserIds.length} members
            </Typography.Text>
          )}
          {conversation?.type === 'BROADCAST' && (
            <Typography.Text type="secondary" style={{ fontSize: 12 }}>
              Org-wide announcements
            </Typography.Text>
          )}
        </div>
        <Button
          type="text"
          icon={<Search size={16} />}
          onClick={() => setSearchOpen(true)}
          aria-label="Search this conversation"
        />
      </header>
      {conversation && <MessageTimeline conversation={conversation} />}
      {conversation && conversation.type !== 'BROADCAST' && (
        <Composer conversationId={conversationId} />
      )}
      {conversation && conversation.type === 'BROADCAST' && (
        <div
          style={{
            padding: 16,
            borderTop: `1px solid ${token.colorBorderSecondary}`,
            background: token.colorFillTertiary,
            color: token.colorTextSecondary,
            fontSize: 13,
            textAlign: 'center',
          }}
        >
          Read-only — only HR can post here. Use the “Send announcement”
          button on the left if you have permission.
        </div>
      )}
      <SearchPanel
        open={searchOpen}
        onClose={() => setSearchOpen(false)}
        conversationId={conversationId}
      />
    </div>
  );
}

function ConversationAvatar({
  type,
  title,
  peerUserId,
}: {
  type: string;
  title: string;
  peerUserId: number | null;
}) {
  if (type === 'BROADCAST')
    return <Avatar size={HEADER_AVATAR_SIZE} icon={<Megaphone size={20} />} />;
  if (type === 'GROUP')
    return <Avatar size={HEADER_AVATAR_SIZE} icon={<Users size={20} />} />;
  return <UserAvatar userId={peerUserId} name={title} size={HEADER_AVATAR_SIZE} />;
}
