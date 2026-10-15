// Per-thread search drawer — wraps GET /chat/search.
// Server requires a conversationId; the response carries the conversation
// header alongside matching messages so we can render thread context
// without an extra round-trip.

import { useQuery } from '@tanstack/react-query';
import { Drawer, Input, theme as antdTheme, Typography } from 'antd';
import { Search } from 'lucide-react';
import { useState } from 'react';
import { useNavigate } from '@tanstack/react-router';
import { EmptyState, RequestState } from '@/components';
import { useDebounced, formatDateTime } from '@/lib';
import { searchApi } from '../api';
import { chatKeys } from '../query-keys';
import { bodyPreview, conversationDisplayTitle } from '../format';

const { Text, Title } = Typography;
const { useToken } = antdTheme;

export type SearchPanelProps = {
  open: boolean;
  onClose: () => void;
  /** Required — search is thread-scoped on the server side. */
  conversationId: number;
};

export function SearchPanel({ open, onClose, conversationId }: SearchPanelProps) {
  const { token } = useToken();
  const [q, setQ] = useState('');
  const debouncedQ = useDebounced(q.trim(), 250);
  const navigate = useNavigate();

  const enabled = open && debouncedQ.length >= 2;
  const query = useQuery({
    queryKey: chatKeys.search(debouncedQ, conversationId),
    queryFn: ({ signal }) =>
      searchApi.search({ q: debouncedQ, conversationId, limit: 50 }, signal),
    enabled,
    staleTime: 30_000,
  });

  const messages = query.data?.messages ?? [];
  const header = query.data?.conversation;

  return (
    <Drawer
      open={open}
      onClose={onClose}
      title="Search in this chat"
      size={520}
      placement="right"
      destroyOnHidden
    >
      <Input
        autoFocus
        prefix={<Search size={14} />}
        placeholder="Type at least two characters"
        value={q}
        onChange={(e) => setQ(e.target.value)}
        allowClear
      />
      {header && enabled && (
        <Title level={5} style={{ margin: '12px 0 0' }}>
          {conversationDisplayTitle(header.type, header.title, null, `Conversation #${header.id}`)}
        </Title>
      )}
      <div style={{ marginTop: 12, minHeight: 200 }}>
        {!enabled && q.length < 2 && (
          <EmptyState
            size="sm"
            icon={Search}
            title="Type to search"
            description="Search runs across messages in this conversation."
          />
        )}
        {enabled && (
          <RequestState
            loading={query.isLoading}
            error={query.error}
            empty={messages.length === 0}
            emptyTitle={`No matches for "${debouncedQ}"`}
            emptyDescription="Try a different word or shorter phrase."
            errorTitle="Couldn't search"
          >
            <ul style={{ listStyle: 'none', margin: 0, padding: 0, display: 'flex', flexDirection: 'column', gap: 8 }}>
              {messages.map((m) => (
                <li
                  key={m.id}
                  onClick={() => {
                    navigate({
                      to: '/chat/$id',
                      params: { id: String(m.conversationId) },
                    });
                    onClose();
                  }}
                  style={{
                    cursor: 'pointer',
                    padding: 10,
                    borderRadius: token.borderRadius,
                    border: `1px solid ${token.colorBorderSecondary}`,
                  }}
                >
                  <Text style={{ display: 'block', fontSize: 13 }}>
                    {bodyPreview(m.body, 140)}
                  </Text>
                  <Text type="secondary" style={{ fontSize: 11 }}>
                    {formatDateTime(m.createdAt)}
                    {m.senderUsername ? ` · @${m.senderUsername}` : ''}
                  </Text>
                </li>
              ))}
            </ul>
          </RequestState>
        )}
      </div>
    </Drawer>
  );
}
