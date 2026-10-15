// HR / ADMIN compliance viewer. Pick a user, list their conversations,
// pick a conversation, view messages. Hard-delete is gated on
// chat.messages.delete; everything else is read-only.

import { useInfiniteQuery, useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { App, Button, Empty, Form, Spin, theme as antdTheme, Typography } from 'antd';
import { useContext, useMemo, useState } from 'react';
import { Trash2 } from 'lucide-react';
import { AbilityContext } from '@/auth';
import { EmptyState, RequestState } from '@/components';
import { formatDateTime, getErrorMessage } from '@/lib';
import { adminChatApi, messagesApi } from '../api';
import { canAdminDelete } from '../permissions';
import { chatKeys } from '../query-keys';
import { conversationDisplayTitle } from '../format';
import { UserPicker } from '../UserPicker';

const { Title, Text } = Typography;
const { useToken } = antdTheme;

export function AdminConversationViewer() {
  const { token } = useToken();
  const [selectedUserId, setSelectedUserId] = useState<number | undefined>();
  const [selectedConvId, setSelectedConvId] = useState<number | undefined>();

  const conversationsQuery = useQuery({
    queryKey: chatKeys.adminConversations(selectedUserId ?? 0),
    queryFn: ({ signal }) => adminChatApi.list(selectedUserId!, signal),
    enabled: selectedUserId != null,
    staleTime: 30_000,
  });

  return (
    <div style={{ display: 'flex', gap: 16, height: '100%', minHeight: 0 }}>
      <div style={{ width: 320, display: 'flex', flexDirection: 'column', gap: 12 }}>
        <Form layout="vertical">
          <Form.Item label="View conversations of">
            <UserPicker
              value={selectedUserId}
              onChange={(id) => {
                setSelectedUserId(id);
                setSelectedConvId(undefined);
              }}
            />
          </Form.Item>
        </Form>
        <div
          style={{
            flex: 1,
            overflowY: 'auto',
            border: `1px solid ${token.colorBorderSecondary}`,
            borderRadius: token.borderRadius,
            minHeight: 0,
          }}
        >
          {selectedUserId == null ? (
            <EmptyState size="sm" title="Pick a user" description="Select someone to load their conversations." />
          ) : (
            <RequestState
              loading={conversationsQuery.isLoading}
              error={conversationsQuery.error}
              empty={!conversationsQuery.data || conversationsQuery.data.length === 0}
              emptyTitle="No conversations"
              errorTitle="Couldn't load conversations"
            >
              <ul style={{ listStyle: 'none', margin: 0, padding: 0 }}>
                {(conversationsQuery.data ?? []).map((c) => (
                  <li
                    key={c.id}
                    onClick={() => setSelectedConvId(c.id)}
                    style={{
                      padding: '8px 12px',
                      cursor: 'pointer',
                      borderBottom: `1px solid ${token.colorBorderSecondary}`,
                      background:
                        selectedConvId === c.id ? token.colorFillSecondary : 'transparent',
                    }}
                  >
                    <Text style={{ display: 'block', fontSize: 13 }}>
                      {conversationDisplayTitle(c.type, c.title, null, `Conv #${c.id}`)}
                    </Text>
                    <Text type="secondary" style={{ fontSize: 11 }}>
                      {c.type} · {c.memberUserIds.length} members
                    </Text>
                  </li>
                ))}
              </ul>
            </RequestState>
          )}
        </div>
      </div>
      <div style={{ flex: 1, minWidth: 0, display: 'flex', flexDirection: 'column' }}>
        {selectedConvId == null ? (
          <EmptyState title="No conversation selected" description="Pick a thread to view its messages." />
        ) : (
          <AdminMessagesPane
            asUserId={selectedUserId!}
            conversationId={selectedConvId}
          />
        )}
      </div>
    </div>
  );
}

function AdminMessagesPane({
  asUserId,
  conversationId,
}: {
  asUserId: number;
  conversationId: number;
}) {
  const { token } = useToken();
  const { message } = App.useApp();
  const ability = useContext(AbilityContext);
  const qc = useQueryClient();
  const canDelete = canAdminDelete(ability);

  const headerQuery = useQuery({
    queryKey: chatKeys.adminConversation(conversationId),
    queryFn: ({ signal }) => adminChatApi.get(conversationId, asUserId, signal),
    staleTime: 30_000,
  });

  const messagesQuery = useInfiniteQuery({
    queryKey: chatKeys.adminMessages(conversationId),
    queryFn: ({ pageParam, signal }) =>
      adminChatApi.messages(
        conversationId,
        { beforeId: pageParam ?? undefined, limit: 50 },
        signal,
      ),
    initialPageParam: undefined as number | undefined,
    getNextPageParam: (last) => {
      if (!last.hasMore || last.items.length === 0) return undefined;
      return last.items[last.items.length - 1]?.id;
    },
  });

  const messages = useMemo(() => {
    if (!messagesQuery.data) return [];
    const flat = messagesQuery.data.pages.flatMap((p) => p.items);
    return [...flat].sort((a, b) => a.id - b.id);
  }, [messagesQuery.data]);

  const deleteMut = useMutation({
    mutationFn: (id: number) => messagesApi.adminRemove(id),
    onSuccess: () => {
      void qc.invalidateQueries({ queryKey: chatKeys.adminMessages(conversationId) });
    },
    onError: (err) => message.error(getErrorMessage(err)),
  });

  return (
    <div style={{ display: 'flex', flexDirection: 'column', height: '100%', minHeight: 0 }}>
      <div
        style={{
          padding: '8px 12px',
          borderBottom: `1px solid ${token.colorBorderSecondary}`,
        }}
      >
        <Title level={5} style={{ margin: 0 }}>
          {conversationDisplayTitle(
            headerQuery.data?.type ?? 'GROUP',
            headerQuery.data?.title ?? null,
            null,
            `Conversation #${conversationId}`,
          )}
        </Title>
        <Text type="secondary" style={{ fontSize: 12 }}>
          Read-only — actions here are audit-emitting.
        </Text>
      </div>
      <div style={{ flex: 1, overflowY: 'auto', padding: 12, minHeight: 0 }}>
        {messagesQuery.isLoading && <Spin />}
        {!messagesQuery.isLoading && messages.length === 0 && <Empty description="No messages" />}
        {messagesQuery.hasNextPage && !messagesQuery.isFetchingNextPage && (
          <div style={{ textAlign: 'center', padding: 8 }}>
            <Button size="small" type="text" onClick={() => messagesQuery.fetchNextPage()}>
              Load older
            </Button>
          </div>
        )}
        <ul style={{ listStyle: 'none', margin: 0, padding: 0, display: 'flex', flexDirection: 'column', gap: 6 }}>
          {messages.map((m) => (
            <li
              key={m.id}
              style={{
                padding: '8px 10px',
                background: token.colorFillSecondary,
                borderRadius: token.borderRadius,
              }}
            >
              <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', gap: 8 }}>
                <Text style={{ fontSize: 12 }}>
                  <strong>User #{m.senderUserId}</strong> · {formatDateTime(m.createdAt)}
                </Text>
                {canDelete && !m.deletedAt && (
                  <Button
                    size="small"
                    type="text"
                    danger
                    icon={<Trash2 size={14} />}
                    onClick={() => deleteMut.mutate(m.id)}
                    loading={deleteMut.isPending}
                  >
                    Delete
                  </Button>
                )}
              </div>
              {m.deletedAt ? (
                <Text type="secondary" italic style={{ fontSize: 13 }}>
                  Removed ({m.deleteReason === 'ADMIN' ? 'HR' : 'sender'})
                </Text>
              ) : (
                <Text style={{ fontSize: 13, whiteSpace: 'pre-wrap' }}>{m.body}</Text>
              )}
            </li>
          ))}
        </ul>
      </div>
    </div>
  );
}
