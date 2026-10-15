// Scrolling message list. Auto-scrolls to the bottom when a new message
// appears or while a reply is streaming, but lets the user manually
// scroll up to read history without fighting the autoscroll.

import { useEffect, useRef } from 'react';

import type { Conversation } from '../mock/data';
import { MessageBubble } from './MessageBubble';

interface TranscriptProps {
  conversation: Conversation;
  streamingMessageId: string | null;
  authorName?: string;
}

export function Transcript({ conversation, streamingMessageId, authorName }: TranscriptProps) {
  const ref = useRef<HTMLDivElement>(null);
  const lastBodyLen = useRef(0);

  useEffect(() => {
    const el = ref.current;
    if (!el) return;

    // Only autoscroll if the user is already near the bottom (within ~80px),
    // so a deliberate scroll-up to read history isn't yanked back down.
    const distanceFromBottom = el.scrollHeight - el.scrollTop - el.clientHeight;
    const last = conversation.messages[conversation.messages.length - 1];
    const lastLen = last?.body.length ?? 0;
    const grew = lastLen > lastBodyLen.current;
    lastBodyLen.current = lastLen;

    if (distanceFromBottom < 80 || grew) {
      el.scrollTop = el.scrollHeight;
    }
  }, [conversation.messages, conversation.id]);

  return (
    <div
      ref={ref}
      style={{
        flex: 1,
        overflowY: 'auto',
        padding: '20px 24px',
        display: 'flex',
        flexDirection: 'column',
        gap: 18,
      }}
    >
      {conversation.messages.map((m) => (
        <MessageBubble
          key={m.id}
          message={m}
          streaming={m.id === streamingMessageId}
          authorName={authorName}
        />
      ))}
    </div>
  );
}
