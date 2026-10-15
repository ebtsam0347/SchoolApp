// Message composer. Owns:
//   - the textarea + send button
//   - draft persistence (per-conversation localStorage)
//   - the debounced typing-ping (POST /typing on first keystroke per ~3s
//     window; POST /typing { stopped:true } on send/blur)
//   - attachment uploads (signed PUT via `attachments.ts`)
//   - parsing `@token` → mentionedUserIds via the resolver cache

import { App, Button, Input, theme as antdTheme, Tooltip, Upload } from 'antd';
import { useMutation, useQueryClient } from '@tanstack/react-query';
import { Paperclip, Send } from 'lucide-react';
import { useEffect, useRef, useState } from 'react';
import { getErrorMessage } from '@/lib';
import { broadcastApi, conversationsApi } from '../api';
import { uploadAttachment } from '../attachments';
import { chatKeys } from '../query-keys';
import { clearDraft, readDraft, writeDraft } from '../drafts';
import type { AttachmentRef, SendMessageRequest } from '../types';

const { useToken } = antdTheme;
const TYPING_PING_INTERVAL_MS = 3000;

export type ComposerProps = {
  conversationId: number;
  /** Disable while loading the conversation, or for read-only Broadcast. */
  disabled?: boolean;
  /** Override placeholder copy (broadcast composer wants different text). */
  placeholder?: string;
  /** When true, sends via /chat/broadcast/messages instead. */
  broadcast?: boolean;
};

// `@token` parsing is intentionally minimal for the first ship: we send the
// `mentionedUserIds` array empty and rely on the bell to be driven by
// future explicit-pick mention chips. The MentionRenderer still highlights
// raw `@token` text so authors can mark intent in the body.
//
// TODO(BFF): expose a username/handle on a user resolver so chat-service
// can fan bell entries to the right user-ids when authors type `@sarah`.

export function Composer({ conversationId, disabled, placeholder, broadcast }: ComposerProps) {
  const { token } = useToken();
  const { message: msg } = App.useApp();
  const qc = useQueryClient();
  const [body, setBody] = useState<string>(() => readDraft(conversationId));
  const [pending, setPending] = useState<AttachmentRef[]>([]);
  const [uploading, setUploading] = useState(false);
  const lastTypingPingAt = useRef<number>(0);

  // Reset draft + state when switching conversation.
  useEffect(() => {
    setBody(readDraft(conversationId));
    setPending([]);
    lastTypingPingAt.current = 0;
  }, [conversationId]);

  // Persist drafts on every change (light-touch — write is cheap).
  useEffect(() => {
    writeDraft(conversationId, body);
  }, [conversationId, body]);

  const sendMut = useMutation({
    mutationFn: (req: SendMessageRequest) =>
      broadcast
        ? broadcastApi.send(req)
        : conversationsApi.send(conversationId, req),
    onSuccess: () => {
      setBody('');
      setPending([]);
      clearDraft(conversationId);
      lastTypingPingAt.current = 0;
      // Server emits the chat.message event over SSE; the dispatcher
      // invalidates messages + conversations. We invalidate eagerly here
      // too so the bubble appears immediately even if SSE is lagging.
      void qc.invalidateQueries({ queryKey: chatKeys.messages(conversationId) });
      void qc.invalidateQueries({ queryKey: chatKeys.conversations });
      void conversationsApi.typing(conversationId, { stopped: true }).catch(() => {});
    },
    onError: (err) => msg.error(getErrorMessage(err)),
  });

  function maybePingTyping() {
    if (broadcast) return;
    const now = Date.now();
    if (now - lastTypingPingAt.current < TYPING_PING_INTERVAL_MS) return;
    lastTypingPingAt.current = now;
    void conversationsApi.typing(conversationId, {}).catch(() => {});
  }

  async function handleUpload(file: File) {
    setUploading(true);
    try {
      const ref = await uploadAttachment(file);
      setPending((p) => [...p, ref]);
    } catch (err) {
      msg.error(getErrorMessage(err));
    } finally {
      setUploading(false);
    }
    // Returning false stops AntD Upload from making its own request.
    return false;
  }

  function handleSend() {
    const trimmed = body.trim();
    if (!trimmed && pending.length === 0) return;
    sendMut.mutate({
      body: trimmed,
      mentionedUserIds: [],
      attachments: pending,
    });
  }

  return (
    <div
      style={{
        borderTop: `1px solid ${token.colorBorderSecondary}`,
        padding: 12,
        display: 'flex',
        flexDirection: 'column',
        gap: 6,
        background: token.colorBgContainer,
      }}
    >
      {pending.length > 0 && (
        <div style={{ display: 'flex', flexWrap: 'wrap', gap: 6 }}>
          {pending.map((p) => (
            <span
              key={p.objectKey}
              style={{
                background: token.colorFillSecondary,
                padding: '4px 8px',
                borderRadius: token.borderRadius,
                fontSize: 12,
                display: 'inline-flex',
                alignItems: 'center',
                gap: 6,
              }}
            >
              <Paperclip size={12} />
              {p.originalName}
              <Button
                size="small"
                type="text"
                onClick={() =>
                  setPending((s) => s.filter((x) => x.objectKey !== p.objectKey))
                }
              >
                ×
              </Button>
            </span>
          ))}
        </div>
      )}
      <div style={{ display: 'flex', alignItems: 'flex-end', gap: 8 }}>
        <Upload
          beforeUpload={handleUpload}
          showUploadList={false}
          disabled={disabled || broadcast === false ? false : false}
          multiple={false}
        >
          <Tooltip title="Attach file">
            <Button
              icon={<Paperclip size={16} />}
              type="text"
              loading={uploading}
              disabled={disabled}
              aria-label="Attach file"
            />
          </Tooltip>
        </Upload>
        <Input.TextArea
          value={body}
          onChange={(e) => {
            setBody(e.target.value);
            if (e.target.value) maybePingTyping();
          }}
          onPressEnter={(e) => {
            if (!e.shiftKey) {
              e.preventDefault();
              handleSend();
            }
          }}
          onBlur={() => {
            if (!broadcast) {
              void conversationsApi
                .typing(conversationId, { stopped: true })
                .catch(() => {});
            }
          }}
          placeholder={placeholder ?? 'Write a message — Shift+Enter for newline'}
          autoSize={{ minRows: 1, maxRows: 6 }}
          disabled={disabled || sendMut.isPending}
          style={{ flex: 1 }}
        />
        <Button
          type="primary"
          icon={<Send size={14} />}
          onClick={handleSend}
          loading={sendMut.isPending}
          disabled={disabled || (!body.trim() && pending.length === 0)}
        >
          Send
        </Button>
      </div>
    </div>
  );
}
