// One assistant or user message in the transcript. Assistant bubbles are
// flush-left on a tinted surface; user bubbles are right-aligned in the
// primary tone. Layout matches the open-ended chat shape rather than a
// strict 2-column SMS layout — short replies sit naturally next to long
// ones.

import { Avatar, theme as antdTheme, Typography } from 'antd';
import { Bot, User } from 'lucide-react';

import type { Message } from '../mock/data';
import { Citations } from './Citations';
import { TypewriterText } from './TypewriterText';

const { Text } = Typography;
const { useToken } = antdTheme;

interface MessageBubbleProps {
  message: Message;
  /** True while this assistant message is being streamed. */
  streaming?: boolean;
  authorName?: string;
}

export function MessageBubble({ message, streaming, authorName }: MessageBubbleProps) {
  const { token } = useToken();
  const isUser = message.role === 'USER';

  return (
    <div
      style={{
        display: 'flex',
        gap: 12,
        flexDirection: isUser ? 'row-reverse' : 'row',
        alignItems: 'flex-start',
      }}
    >
      <Avatar
        size={32}
        icon={isUser ? <User size={16} strokeWidth={1.75} /> : <Bot size={16} strokeWidth={1.75} />}
        style={{
          flexShrink: 0,
          background: isUser ? token.colorPrimary : token.colorFillSecondary,
          color: isUser ? token.colorTextLightSolid : token.colorPrimary,
        }}
      />
      <div
        style={{
          maxWidth: '78%',
          display: 'flex',
          flexDirection: 'column',
          gap: 4,
          alignItems: isUser ? 'flex-end' : 'flex-start',
        }}
      >
        <Text
          type="secondary"
          style={{ fontSize: 11, fontWeight: 500, letterSpacing: '0.02em' }}
        >
          {isUser ? authorName ?? 'You' : 'Assistant'}
          {message.model ? (
            <span style={{ marginLeft: 6, opacity: 0.7 }}>· {message.model.split(':')[0]}</span>
          ) : null}
        </Text>
        <div
          style={{
            padding: '10px 14px',
            borderRadius: 12,
            borderTopRightRadius: isUser ? 4 : 12,
            borderTopLeftRadius: isUser ? 12 : 4,
            background: isUser ? token.colorPrimary : token.colorFillTertiary,
            color: isUser ? token.colorTextLightSolid : token.colorText,
            fontSize: 14,
            lineHeight: 1.55,
            wordBreak: 'break-word',
          }}
        >
          {isUser ? (
            <span style={{ whiteSpace: 'pre-wrap' }}>{message.body}</span>
          ) : (
            <TypewriterText text={message.body} streaming={!!streaming} />
          )}
        </div>
        {!isUser && message.citations && message.citations.length > 0 ? (
          <Citations citations={message.citations} />
        ) : null}
      </div>
    </div>
  );
}
