import { App, Button, Dropdown, Input, theme as antdTheme, Typography } from 'antd';
import { useMutation, useQueryClient } from '@tanstack/react-query';
import { MoreHorizontal, Pencil, Trash2 } from 'lucide-react';
import { useState } from 'react';
import { formatDateTime, getErrorMessage } from '@/lib';
import type { MessageDto } from '../types';
import { messagesApi } from '../api';
import { chatKeys } from '../query-keys';
import { useUserName } from '../user-resolver';
import { UserAvatar } from '../UserAvatar';
import { AttachmentChip } from './AttachmentChip';
import { DeliveryTicks } from './DeliveryTicks';
import { MentionRenderer } from './MentionRenderer';

const { Text } = Typography;
const { useToken } = antdTheme;

const EDIT_WINDOW_MS = 15 * 60 * 1000;

export type MessageBubbleProps = {
  message: MessageDto;
  outgoing: boolean;
  /** Whether the conversation is a Broadcast — disables edit/delete UI for non-senders. */
  broadcast?: boolean;
  /** Sender's display name override; falls back to the resolver. */
  senderName?: string | null;
  /** Outgoing-only: any peer has read up to or past this message id. */
  delivered?: boolean;
  /** Outgoing-only: every peer has read up to or past this message id. */
  read?: boolean;
};

export function MessageBubble({
  message,
  outgoing,
  broadcast,
  senderName,
  delivered,
  read,
}: MessageBubbleProps) {
  const { token } = useToken();
  const { message: msg } = App.useApp();
  const qc = useQueryClient();
  // Prefer the resolver cache (always in sync with /bff/users/by-ids).
  // Fall back to the persisted senderUsername the backend stamps on every
  // message, then to a `User #N` placeholder.
  const resolvedName = useUserName(message.senderUserId);
  const displayName =
    senderName ??
    (resolvedName.startsWith('User #')
      ? message.senderUsername ?? resolvedName
      : resolvedName);
  const [editing, setEditing] = useState(false);
  const [draft, setDraft] = useState(message.body);

  const editable =
    outgoing &&
    !message.deletedAt &&
    !broadcast &&
    Date.now() - new Date(message.createdAt).getTime() < EDIT_WINDOW_MS;

  const editMut = useMutation({
    mutationFn: () => messagesApi.edit(message.id, { body: draft.trim() }),
    onSuccess: () => {
      setEditing(false);
      void qc.invalidateQueries({ queryKey: chatKeys.messages(message.conversationId) });
    },
    onError: (err) => msg.error(getErrorMessage(err)),
  });

  const deleteMut = useMutation({
    mutationFn: () => messagesApi.remove(message.id),
    onSuccess: () => {
      void qc.invalidateQueries({ queryKey: chatKeys.messages(message.conversationId) });
    },
    onError: (err) => msg.error(getErrorMessage(err)),
  });

  if (message.deletedAt) {
    return (
      <div style={bubbleWrapStyle(outgoing)}>
        <div
          style={{
            ...bubbleStyle(outgoing, token, true),
            fontStyle: 'italic',
            opacity: 0.7,
          }}
        >
          <Text type="secondary" style={{ fontSize: 12 }}>
            {message.deleteReason === 'ADMIN'
              ? 'Removed by HR'
              : 'Removed by sender'}
          </Text>
        </div>
      </div>
    );
  }

  const actionItems = [
    editable
      ? {
          key: 'edit',
          icon: <Pencil size={14} />,
          label: 'Edit',
          onClick: () => setEditing(true),
        }
      : null,
    outgoing
      ? {
          key: 'delete',
          icon: <Trash2 size={14} />,
          label: 'Delete',
          danger: true,
          onClick: () => deleteMut.mutate(),
        }
      : null,
  ].filter((x): x is NonNullable<typeof x> => x !== null);

  return (
    <div style={bubbleWrapStyle(outgoing)}>
      <div style={{ maxWidth: '70%', display: 'flex', flexDirection: 'column', gap: 4 }}>
        {!outgoing && (
          <div style={{ display: 'flex', alignItems: 'center', gap: 8, paddingLeft: 4 }}>
            <UserAvatar userId={message.senderUserId} name={displayName} size={32} />
            <Text type="secondary" style={{ fontSize: 12 }}>
              {displayName}
            </Text>
          </div>
        )}
        <div style={bubbleStyle(outgoing, token, false)}>
          {editing ? (
            <div style={{ display: 'flex', flexDirection: 'column', gap: 6 }}>
              <Input.TextArea
                value={draft}
                onChange={(e) => setDraft(e.target.value)}
                autoSize={{ minRows: 1, maxRows: 8 }}
              />
              <div style={{ display: 'flex', gap: 6, justifyContent: 'flex-end' }}>
                <Button size="small" onClick={() => { setEditing(false); setDraft(message.body); }}>
                  Cancel
                </Button>
                <Button
                  size="small"
                  type="primary"
                  loading={editMut.isPending}
                  onClick={() => editMut.mutate()}
                  disabled={!draft.trim() || draft.trim() === message.body}
                >
                  Save
                </Button>
              </div>
            </div>
          ) : (
            <>
              <MentionRenderer body={message.body} />
              {message.attachments.length > 0 && (
                <div style={{ display: 'flex', flexWrap: 'wrap', gap: 6, marginTop: 6 }}>
                  {message.attachments.map((a) => (
                    <AttachmentChip key={a.id} attachment={a} />
                  ))}
                </div>
              )}
            </>
          )}
        </div>
        <div
          style={{
            display: 'flex',
            alignItems: 'center',
            gap: 6,
            justifyContent: outgoing ? 'flex-end' : 'flex-start',
            paddingInline: 4,
          }}
        >
          <Text type="secondary" style={{ fontSize: 11 }}>
            {formatDateTime(message.createdAt)}
          </Text>
          {message.editedAt && (
            <Text type="secondary" style={{ fontSize: 11 }}>
              · edited
            </Text>
          )}
          {outgoing && !broadcast && (
            <DeliveryTicks delivered={delivered} read={read} />
          )}
          {actionItems.length > 0 && !editing && (
            <Dropdown menu={{ items: actionItems }} trigger={['click']}>
              <Button
                size="small"
                type="text"
                icon={<MoreHorizontal size={14} />}
                aria-label="Message actions"
              />
            </Dropdown>
          )}
        </div>
      </div>
    </div>
  );
}

function bubbleWrapStyle(outgoing: boolean): React.CSSProperties {
  return {
    display: 'flex',
    justifyContent: outgoing ? 'flex-end' : 'flex-start',
    width: '100%',
  };
}

function bubbleStyle(
  outgoing: boolean,
  token: ReturnType<typeof useToken>['token'],
  ghosted: boolean,
): React.CSSProperties {
  return {
    background: ghosted
      ? 'transparent'
      : outgoing
        ? token.colorPrimary
        : token.colorFillSecondary,
    color: ghosted ? token.colorTextSecondary : outgoing ? token.colorTextLightSolid : token.colorText,
    padding: '8px 12px',
    borderRadius: token.borderRadiusLG,
    border: ghosted ? `1px dashed ${token.colorBorderSecondary}` : 'none',
    fontSize: 14,
    lineHeight: 1.4,
  };
}
