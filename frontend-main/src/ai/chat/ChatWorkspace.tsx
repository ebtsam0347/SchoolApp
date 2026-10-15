// Headline page of the Assistant workspace. Owns:
//
//   - reading the conversation list + active conversation from the mock
//     store via TanStack Query
//   - the composer state (input text, streaming flag, abort controller)
//   - the streaming pipeline: mockStream() → append deltas via
//     setQueryData → patch persisted message → stamp on `done`
//
// The visual layout is a nested 2-pane: a 280px conversations rail on
// the left and the transcript + composer filling the rest. The outer
// AppShell sidebar stays visible (matches the rest of the app — we
// don't take over the whole window).

import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { theme as antdTheme } from 'antd';
import { useRef, useState } from 'react';

import { PreviewNotice } from '../PreviewNotice';
import { currentUser, type Conversation, type Message, pickReply } from '../mock/data';
import { mockStore } from '../mock/store';
import { mockStream } from '../mock/stream';
import { ChatHeader } from './ChatHeader';
import { Composer } from './Composer';
import { ConversationsList } from './ConversationsList';
import { EmptyChat } from './EmptyChat';
import { Transcript } from './Transcript';

const { useToken } = antdTheme;

const LIST_KEY = ['ai', 'conversations'] as const;
const convKey = (id: string) => ['ai', 'conversation', id] as const;

interface ChatWorkspaceProps {
  /** From the URL search param `?c=...`. `null` means "no conversation selected". */
  activeId: string | null;
  setActiveId: (id: string | null) => void;
}

export function ChatWorkspace({ activeId, setActiveId }: ChatWorkspaceProps) {
  const { token } = useToken();
  const qc = useQueryClient();

  const conversationsQuery = useQuery({
    queryKey: LIST_KEY,
    queryFn: () => mockStore.listConversations(),
    staleTime: Infinity,
  });

  const activeQuery = useQuery({
    queryKey: activeId ? convKey(activeId) : ['ai', 'conversation', 'none'],
    queryFn: () => (activeId ? mockStore.getConversation(activeId) ?? null : null),
    enabled: activeId !== null,
    staleTime: Infinity,
  });

  const [composerValue, setComposerValue] = useState('');
  const [streamingMessageId, setStreamingMessageId] = useState<string | null>(null);
  const abortRef = useRef<AbortController | null>(null);

  const renameMutation = useMutation({
    mutationFn: async ({ id, title }: { id: string; title: string }) => {
      mockStore.rename(id, title);
    },
    onSuccess: (_data, vars) => {
      qc.invalidateQueries({ queryKey: LIST_KEY });
      qc.invalidateQueries({ queryKey: convKey(vars.id) });
    },
  });

  const archiveMutation = useMutation({
    mutationFn: async (id: string) => mockStore.archive(id),
    onSuccess: (_data, id) => {
      qc.invalidateQueries({ queryKey: LIST_KEY });
      if (activeId === id) setActiveId(null);
    },
  });

  const newConversation = () => {
    const conv = mockStore.createConversation('New conversation', currentUser.id, currentUser.name);
    qc.invalidateQueries({ queryKey: LIST_KEY });
    setActiveId(conv.id);
    setComposerValue('');
  };

  /** Append a freshly-typed user message + start streaming the canned reply. */
  const send = async () => {
    const body = composerValue.trim();
    if (!body || streamingMessageId) return;

    // Materialise an active conversation if there isn't one yet.
    let convId = activeId;
    let convTitle = '';
    if (!convId) {
      const conv = mockStore.createConversation(body, currentUser.id, currentUser.name);
      convId = conv.id;
      convTitle = body;
      qc.invalidateQueries({ queryKey: LIST_KEY });
      setActiveId(convId);
    }

    // Persist the user turn.
    const now = new Date().toISOString();
    const userMsg: Message = {
      id: `m-${Date.now()}-u`,
      role: 'USER',
      body,
      createdAt: now,
    };
    mockStore.appendMessage(convId, userMsg, convTitle || undefined);

    // Seed an empty assistant bubble we'll stream into.
    const assistantId = `m-${Date.now()}-a`;
    const assistantSeed: Message = {
      id: assistantId,
      role: 'ASSISTANT',
      body: '',
      createdAt: new Date().toISOString(),
      model: 'qwen2.5:7b-instruct-q4_K_M',
    };
    mockStore.appendMessage(convId, assistantSeed);

    // Refresh queries so the UI shows both bubbles immediately.
    qc.invalidateQueries({ queryKey: convKey(convId) });
    qc.invalidateQueries({ queryKey: LIST_KEY });

    setComposerValue('');
    setStreamingMessageId(assistantId);

    const reply = pickReply(body);
    const controller = new AbortController();
    abortRef.current = controller;

    let assembled = '';
    try {
      for await (const ev of mockStream(reply.body, {
        signal: controller.signal,
        citations: reply.citations,
      })) {
        if (ev.type === 'delta') {
          assembled += ev.text;
          // Patch the persisted message body. Cheaper paths exist (debounced
          // writes), but for a mock the localStorage cost is trivial.
          mockStore.patchMessage(convId, assistantId, { body: assembled });
          qc.setQueryData(convKey(convId), (prev: Conversation | null | undefined) => {
            if (!prev) return prev;
            return {
              ...prev,
              messages: prev.messages.map((m) =>
                m.id === assistantId ? { ...m, body: assembled } : m,
              ),
            };
          });
        } else if (ev.type === 'done') {
          mockStore.patchMessage(convId, assistantId, {
            body: assembled,
            citations: ev.citations,
            promptTokens: ev.promptTokens,
            completionTokens: ev.completionTokens,
          });
          qc.invalidateQueries({ queryKey: convKey(convId) });
          qc.invalidateQueries({ queryKey: LIST_KEY });
        }
      }
    } finally {
      setStreamingMessageId(null);
      abortRef.current = null;
    }
  };

  const stop = () => {
    abortRef.current?.abort();
  };

  const conversations = conversationsQuery.data ?? [];
  const active = (activeQuery.data ?? null) as Conversation | null;
  const showEmpty = !active || active.messages.length === 0;

  return (
    <div
      style={{
        display: 'flex',
        height: '100%',
        minHeight: 'calc(100vh - 200px)',
        borderRadius: 12,
        overflow: 'hidden',
        border: `1px solid ${token.colorBorderSecondary}`,
        background: token.colorBgContainer,
      }}
    >
      <ConversationsList
        conversations={conversations}
        activeId={activeId}
        onSelect={(id) => setActiveId(id)}
        onNew={newConversation}
        onRename={(id, title) => renameMutation.mutate({ id, title })}
        onArchive={(id) => archiveMutation.mutate(id)}
      />

      <div
        style={{
          flex: 1,
          display: 'flex',
          flexDirection: 'column',
          minWidth: 0,
        }}
      >
        <ChatHeader conversation={active} />
        <div
          style={{
            flex: 1,
            display: 'flex',
            flexDirection: 'column',
            minHeight: 0,
            background: token.colorBgLayout,
          }}
        >
          <div style={{ padding: '16px 24px 0' }}>
            <PreviewNotice />
          </div>
          {showEmpty ? (
            <EmptyChat onPick={(p) => setComposerValue(p)} />
          ) : (
            <Transcript
              conversation={active!}
              streamingMessageId={streamingMessageId}
              authorName={currentUser.name}
            />
          )}
          <Composer
            value={composerValue}
            onChange={setComposerValue}
            onSend={send}
            onStop={stop}
            streaming={streamingMessageId !== null}
          />
        </div>
      </div>
    </div>
  );
}
