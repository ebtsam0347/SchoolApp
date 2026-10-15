// Slim header above the transcript — conversation title, last-updated
// timestamp, and a "model" badge. Mostly for context; the conversation
// list already shows the title.

import { Tag, Typography, theme as antdTheme } from 'antd';
import { Bot } from 'lucide-react';

import { formatDateTime } from '@/lib/dates';

import type { Conversation } from '../mock/data';

const { Text, Title } = Typography;
const { useToken } = antdTheme;

interface ChatHeaderProps {
  conversation: Conversation | null;
}

export function ChatHeader({ conversation }: ChatHeaderProps) {
  const { token } = useToken();

  return (
    <header
      style={{
        padding: '12px 24px',
        borderBottom: `1px solid ${token.colorBorderSecondary}`,
        background: token.colorBgContainer,
        display: 'flex',
        alignItems: 'center',
        justifyContent: 'space-between',
        gap: 12,
        flexShrink: 0,
      }}
    >
      <div style={{ minWidth: 0, flex: 1 }}>
        <Title
          level={5}
          style={{
            margin: 0,
            whiteSpace: 'nowrap',
            overflow: 'hidden',
            textOverflow: 'ellipsis',
          }}
        >
          {conversation?.title ?? 'New conversation'}
        </Title>
        {conversation ? (
          <Text type="secondary" style={{ fontSize: 12 }}>
            Updated {formatDateTime(conversation.updatedAt)} · {conversation.messages.length}{' '}
            {conversation.messages.length === 1 ? 'message' : 'messages'}
          </Text>
        ) : (
          <Text type="secondary" style={{ fontSize: 12 }}>
            Pick a conversation on the left, or ask me something to start a new one.
          </Text>
        )}
      </div>
      <Tag
        icon={<Bot size={11} strokeWidth={1.75} />}
        style={{
          margin: 0,
          fontSize: 11,
          fontWeight: 500,
          padding: '2px 8px',
          display: 'inline-flex',
          alignItems: 'center',
          gap: 4,
        }}
      >
        qwen2.5:7b
      </Tag>
    </header>
  );
}
