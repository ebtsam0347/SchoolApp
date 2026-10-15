import { Avatar, Badge, theme as antdTheme, Typography } from 'antd';
import { Megaphone, Users } from 'lucide-react';
import type { ConversationDto } from '../types';
import { conversationDisplayTitle } from '../format';
import { useUserDisplay } from '../user-resolver';
import { UserAvatar } from '../UserAvatar';
import { formatDateMonthDay } from '@/lib';

const { Text } = Typography;
const { useToken } = antdTheme;

/** Big enough to actually recognise a face in the list. Group/broadcast
 *  icon avatars use the same size so rows stay on one baseline. */
const ROW_AVATAR_SIZE = 44;

export type ConversationRowProps = {
  conversation: ConversationDto;
  selected?: boolean;
  selfUserId: number | null;
  onSelect: () => void;
};

export function ConversationRow({
  conversation,
  selected,
  selfUserId,
  onSelect,
}: ConversationRowProps) {
  const { token } = useToken();

  // For DMs the "title" is the peer's name. memberUserIds usually has 2
  // entries; the peer is the one that isn't us.
  const peerUserId =
    conversation.type === 'DM' && selfUserId != null
      ? conversation.memberUserIds.find((id) => id !== selfUserId) ?? null
      : null;
  const peerDisplay = useUserDisplay(peerUserId ?? undefined);
  const title = conversationDisplayTitle(
    conversation.type,
    conversation.title,
    peerDisplay?.fullName ?? (peerUserId ? `User #${peerUserId}` : null),
  );

  const lastAt = conversation.lastMessageAt
    ? formatDateMonthDay(conversation.lastMessageAt)
    : null;

  return (
    <li
      onClick={onSelect}
      style={{
        display: 'flex',
        alignItems: 'center',
        gap: 12,
        padding: '10px 16px',
        cursor: 'pointer',
        background: selected ? token.colorFillSecondary : 'transparent',
        borderLeft: selected
          ? `3px solid ${token.colorPrimary}`
          : '3px solid transparent',
        transition: `background ${token.motionDurationMid} ease`,
      }}
      onMouseEnter={(e) => {
        if (!selected) e.currentTarget.style.background = token.colorFillTertiary;
      }}
      onMouseLeave={(e) => {
        if (!selected) e.currentTarget.style.background = 'transparent';
      }}
    >
      <ConversationAvatar
        conversation={conversation}
        title={title}
        peerUserId={peerUserId}
      />
      <div style={{ flex: 1, minWidth: 0 }}>
        <div
          style={{
            display: 'flex',
            justifyContent: 'space-between',
            alignItems: 'center',
            gap: 8,
          }}
        >
          <Text
            strong={conversation.unreadCount > 0}
            ellipsis
            style={{ fontSize: 13 }}
          >
            {title}
          </Text>
          {lastAt && (
            <Text
              type="secondary"
              style={{ fontSize: 11, flexShrink: 0 }}
            >
              {lastAt}
            </Text>
          )}
        </div>
        <div
          style={{
            display: 'flex',
            justifyContent: 'space-between',
            alignItems: 'center',
            gap: 8,
            marginTop: 2,
          }}
        >
          <Text
            type="secondary"
            ellipsis
            style={{ fontSize: 12 }}
          >
            {conversation.lastMessageId
              ? `Message #${conversation.lastMessageId}`
              : 'No messages yet'}
          </Text>
          {conversation.unreadCount > 0 && (
            <Badge
              count={conversation.unreadCount}
              size="small"
              style={{ flexShrink: 0 }}
            />
          )}
        </div>
      </div>
    </li>
  );
}

function ConversationAvatar({
  conversation,
  title,
  peerUserId,
}: {
  conversation: ConversationDto;
  title: string;
  peerUserId: number | null;
}) {
  if (conversation.type === 'BROADCAST') {
    return <Avatar size={ROW_AVATAR_SIZE} icon={<Megaphone size={20} />} />;
  }
  if (conversation.type === 'GROUP') {
    return <Avatar size={ROW_AVATAR_SIZE} icon={<Users size={20} />} />;
  }
  // DM — the row stands for the peer, so show their picture.
  return <UserAvatar userId={peerUserId} name={title} size={ROW_AVATAR_SIZE} />;
}
