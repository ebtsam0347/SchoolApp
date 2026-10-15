// Backwards-paged message timeline. Newest at the bottom; "load older"
// triggers when the user scrolls within ~100px of the top. Auto-marks the
// conversation as read on every new tail message.

import { useInfiniteQuery, useMutation } from '@tanstack/react-query';
import { App, Button, Spin, theme as antdTheme, Typography } from 'antd';
import { useEffect, useMemo, useRef } from 'react';
import { useAuth } from '@/auth';
import { EmptyState, RequestState } from '@/components';
import { getErrorMessage } from '@/lib';
import { conversationsApi } from '../api';
import { chatKeys } from '../query-keys';
import type { ConversationDto, MessageDto } from '../types';
import { useTypingFor } from '../presence-store';
import { seedUsersFallback, useResolveUsers, useUserName } from '../user-resolver';
import { MessageBubble } from './MessageBubble';
import { MessageSquare } from 'lucide-react';

const { Text } = Typography;
const { useToken } = antdTheme;

export type MessageTimelineProps = {
  conversation: ConversationDto;
};

export function MessageTimeline({ conversation }: MessageTimelineProps) {
  const { message } = App.useApp();
  const auth = useAuth();
  const selfId = auth.user?.userId ?? null;
  const scrollRef = useRef<HTMLDivElement>(null);
  const lastSeenIdRef = useRef<number | null>(null);

  // Pull display names for everyone in the conversation up-front so bubble
  // headers and the typing indicator render properly.
  useResolveUsers(conversation.memberUserIds);

  // Per-message delivery/read calculation from the conversation's
  // `lastReadByUser` map. For each outgoing message:
  //   - delivered = any non-self member has read at or past the message id
  //   - read      = every non-self member has read at or past the message id
  // The map lives on the conversation header, so we recompute when the
  // conversation refetches.
  const peerReads = (() => {
    const peers = conversation.memberUserIds.filter((u) => u !== selfId);
    const cursors = peers.map(
      (u) => conversation.lastReadByUser?.[String(u)] ?? 0,
    );
    return { peers, cursors };
  })();
  function deliveryStateFor(messageId: number): { delivered: boolean; read: boolean } {
    if (peerReads.peers.length === 0) return { delivered: false, read: false };
    const delivered = peerReads.cursors.some((c) => c >= messageId);
    const read = peerReads.cursors.every((c) => c >= messageId);
    return { delivered, read };
  }

  const query = useInfiniteQuery({
    queryKey: chatKeys.messages(conversation.id),
    queryFn: ({ pageParam, signal }) =>
      conversationsApi.messages(
        conversation.id,
        { beforeId: pageParam ?? undefined, limit: 50 },
        signal,
      ),
    initialPageParam: undefined as number | undefined,
    getNextPageParam: (last) => {
      if (!last.hasMore || last.items.length === 0) return undefined;
      return last.items[last.items.length - 1]?.id;
    },
  });

  const allMessages: MessageDto[] = useMemo(() => {
    if (!query.data) return [];
    // Server returns newest-first per page; flatten and reverse so the
    // bottom of the array is newest. (Pages stack: page0 = newest 50,
    // page1 = next 50 older.)
    const flat = query.data.pages.flatMap((p) => p.items);
    return [...flat].sort((a, b) => a.id - b.id);
  }, [query.data]);

  // Seed sender usernames from messages as a *fallback* only — every
  // message ships with the author's username at send-time, so we can
  // render *something* before /bff/users/by-ids returns. We must not
  // overwrite the richer entries the inbox composite already seeded
  // (those have the real full name + avatar) — `seedUsersFallback` only
  // fills empty cache slots.
  useEffect(() => {
    if (allMessages.length === 0) return;
    const seen = new Map<number, string>();
    for (const m of allMessages) {
      if (m.senderUsername && !seen.has(m.senderUserId)) {
        seen.set(m.senderUserId, m.senderUsername);
      }
    }
    if (seen.size === 0) return;
    seedUsersFallback(
      Array.from(seen.entries()).map(([userId, username]) => ({
        userId,
        display: { fullName: username, username },
      })),
    );
  }, [allMessages]);

  // Auto-scroll to bottom when a new tail message arrives.
  useEffect(() => {
    const el = scrollRef.current;
    if (!el || allMessages.length === 0) return;
    const newestId = allMessages[allMessages.length - 1]!.id;
    if (lastSeenIdRef.current === newestId) return;
    el.scrollTop = el.scrollHeight;
    lastSeenIdRef.current = newestId;
  }, [allMessages]);

  // Mark-read mutation. Server is idempotent so we fire on every newest-id change.
  const readMut = useMutation({
    mutationFn: (messageId: number) =>
      conversationsApi.read(conversation.id, { lastReadMessageId: messageId }),
    onError: (err) => message.error(getErrorMessage(err)),
  });

  // Mark-read on every newest-id transition. Track the last id we acked in a
  // ref so we don't re-fire when the array identity changes but the tail
  // message is the same.
  const lastReadAckRef = useRef<number | null>(null);
  useEffect(() => {
    if (allMessages.length === 0) return;
    const newest = allMessages[allMessages.length - 1]!;
    if (newest.senderUserId === selfId) return;
    if (lastReadAckRef.current === newest.id) return;
    lastReadAckRef.current = newest.id;
    readMut.mutate(newest.id);
  }, [allMessages, selfId, readMut]);

  // Load-older on scroll-to-top.
  function handleScroll(e: React.UIEvent<HTMLDivElement>) {
    const el = e.currentTarget;
    if (el.scrollTop < 80 && query.hasNextPage && !query.isFetchingNextPage) {
      void query.fetchNextPage();
    }
  }

  const typingUserIds = useTypingFor(conversation.id).filter((id) => id !== selfId);

  return (
    <RequestState
      loading={query.isLoading}
      error={query.error}
      empty={allMessages.length === 0}
      loadingFallback={
        <div style={{ display: 'flex', justifyContent: 'center', padding: 24 }}>
          <Spin />
        </div>
      }
      emptyFallback={
        <EmptyState
          icon={MessageSquare}
          title="No messages yet"
          description="Send the first message to start the conversation."
        />
      }
      errorTitle="Couldn't load messages"
    >
      <div
        ref={scrollRef}
        onScroll={handleScroll}
        style={{
          flex: 1,
          overflowY: 'auto',
          padding: '12px 16px',
          display: 'flex',
          flexDirection: 'column',
          gap: 8,
          minHeight: 0,
        }}
      >
        {query.isFetchingNextPage && (
          <div style={{ textAlign: 'center', padding: 8 }}>
            <Spin size="small" />
          </div>
        )}
        {query.hasNextPage && !query.isFetchingNextPage && (
          <div style={{ textAlign: 'center', padding: 8 }}>
            <Button size="small" type="text" onClick={() => query.fetchNextPage()}>
              Load older messages
            </Button>
          </div>
        )}
        {allMessages.map((m) => {
          const outgoing = m.senderUserId === selfId;
          const { delivered, read } = outgoing
            ? deliveryStateFor(m.id)
            : { delivered: false, read: false };
          return (
            <MessageBubble
              key={m.id}
              message={m}
              outgoing={outgoing}
              broadcast={conversation.type === 'BROADCAST'}
              delivered={delivered}
              read={read}
            />
          );
        })}
        {typingUserIds.length > 0 && (
          <TypingIndicator userIds={typingUserIds} />
        )}
      </div>
    </RequestState>
  );
}

function TypingIndicator({ userIds }: { userIds: number[] }) {
  const { token } = useToken();
  const firstName = useUserName(userIds[0]);
  const label =
    userIds.length === 1
      ? `${firstName} is typing…`
      : `${userIds.length} people are typing…`;
  return (
    <div
      style={{
        padding: '4px 12px',
        background: token.colorFillTertiary,
        borderRadius: token.borderRadiusLG,
        alignSelf: 'flex-start',
        display: 'flex',
        alignItems: 'center',
        gap: 6,
        fontSize: 12,
        color: token.colorTextSecondary,
      }}
    >
      <DotsAnimation />
      <Text type="secondary" style={{ fontSize: 12 }}>
        {label}
      </Text>
    </div>
  );
}

function DotsAnimation() {
  const { token } = useToken();
  return (
    <span style={{ display: 'inline-flex', gap: 2 }}>
      {[0, 1, 2].map((i) => (
        <span
          key={i}
          style={{
            width: 4,
            height: 4,
            borderRadius: '50%',
            background: token.colorTextSecondary,
            animation: 'fshrms-typing 1.2s ease-in-out infinite',
            animationDelay: `${i * 0.15}s`,
          }}
        />
      ))}
      <style>{`
        @keyframes fshrms-typing {
          0%, 60%, 100% { transform: translateY(0); opacity: 0.4; }
          30% { transform: translateY(-3px); opacity: 1; }
        }
      `}</style>
    </span>
  );
}
