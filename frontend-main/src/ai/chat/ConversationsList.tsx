// Left rail of the chat workspace — grouped conversation history with
// rename / archive on hover. Groups are by recency: Today / Yesterday /
// Last 7 days / Older. Click a row to switch the active conversation.

import { Button, Dropdown, Input, Modal, Tooltip, Typography, theme as antdTheme } from 'antd';
import { Archive, MessageSquarePlus, MoreHorizontal, Pencil } from 'lucide-react';
import { useState } from 'react';

import type { Conversation } from '../mock/data';

const { Text } = Typography;
const { useToken } = antdTheme;

interface ConversationsListProps {
  conversations: Conversation[];
  activeId: string | null;
  onSelect: (id: string) => void;
  onNew: () => void;
  onRename: (id: string, title: string) => void;
  onArchive: (id: string) => void;
}

interface Group {
  label: string;
  items: Conversation[];
}

function groupByRecency(conversations: Conversation[]): Group[] {
  const today = new Date('2026-05-04').toISOString().slice(0, 10);
  const yesterday = new Date('2026-05-03').toISOString().slice(0, 10);
  const sevenAgo = new Date('2026-04-27').toISOString().slice(0, 10);

  const todays: Conversation[] = [];
  const yesterdays: Conversation[] = [];
  const week: Conversation[] = [];
  const older: Conversation[] = [];

  for (const c of conversations) {
    const d = c.updatedAt.slice(0, 10);
    if (d >= today) todays.push(c);
    else if (d >= yesterday) yesterdays.push(c);
    else if (d >= sevenAgo) week.push(c);
    else older.push(c);
  }

  const groups: Group[] = [];
  if (todays.length) groups.push({ label: 'Today', items: todays });
  if (yesterdays.length) groups.push({ label: 'Yesterday', items: yesterdays });
  if (week.length) groups.push({ label: 'Last 7 days', items: week });
  if (older.length) groups.push({ label: 'Older', items: older });
  return groups;
}

export function ConversationsList({
  conversations,
  activeId,
  onSelect,
  onNew,
  onRename,
  onArchive,
}: ConversationsListProps) {
  const { token } = useToken();
  const [renaming, setRenaming] = useState<{ id: string; current: string } | null>(null);
  const [renameValue, setRenameValue] = useState('');

  const groups = groupByRecency(conversations);

  return (
    <aside
      style={{
        width: 280,
        flexShrink: 0,
        borderRight: `1px solid ${token.colorBorderSecondary}`,
        display: 'flex',
        flexDirection: 'column',
        background: token.colorBgContainer,
      }}
    >
      <div style={{ padding: 12 }}>
        <Button
          type="primary"
          icon={<MessageSquarePlus size={14} strokeWidth={1.75} />}
          onClick={onNew}
          block
        >
          New conversation
        </Button>
      </div>

      <div style={{ flex: 1, overflowY: 'auto', padding: '4px 8px 16px' }}>
        {groups.length === 0 ? (
          <div style={{ padding: 16, textAlign: 'center' }}>
            <Text type="secondary" style={{ fontSize: 12 }}>
              No conversations yet. Start one above.
            </Text>
          </div>
        ) : (
          groups.map((g) => (
            <div key={g.label} style={{ marginBottom: 12 }}>
              <div
                style={{
                  fontSize: 10,
                  fontWeight: 600,
                  letterSpacing: '0.08em',
                  textTransform: 'uppercase',
                  color: token.colorTextTertiary,
                  padding: '6px 10px 4px',
                }}
              >
                {g.label}
              </div>
              {g.items.map((c) => {
                const active = c.id === activeId;
                return (
                  <ConversationRow
                    key={c.id}
                    conversation={c}
                    active={active}
                    onClick={() => onSelect(c.id)}
                    onRename={() => {
                      setRenaming({ id: c.id, current: c.title });
                      setRenameValue(c.title);
                    }}
                    onArchive={() => onArchive(c.id)}
                  />
                );
              })}
            </div>
          ))
        )}
      </div>

      <Modal
        title="Rename conversation"
        open={renaming !== null}
        onCancel={() => setRenaming(null)}
        onOk={() => {
          if (renaming && renameValue.trim()) {
            onRename(renaming.id, renameValue.trim());
            setRenaming(null);
          }
        }}
        okText="Rename"
        destroyOnHidden
      >
        <Input
          autoFocus
          value={renameValue}
          onChange={(e) => setRenameValue(e.target.value)}
          maxLength={80}
          onPressEnter={() => {
            if (renaming && renameValue.trim()) {
              onRename(renaming.id, renameValue.trim());
              setRenaming(null);
            }
          }}
        />
      </Modal>
    </aside>
  );
}

interface ConversationRowProps {
  conversation: Conversation;
  active: boolean;
  onClick: () => void;
  onRename: () => void;
  onArchive: () => void;
}

function ConversationRow({
  conversation,
  active,
  onClick,
  onRename,
  onArchive,
}: ConversationRowProps) {
  const { token } = useToken();
  const [hovered, setHovered] = useState(false);

  return (
    <div
      onMouseEnter={() => setHovered(true)}
      onMouseLeave={() => setHovered(false)}
      style={{ position: 'relative' }}
    >
      <button
        type="button"
        onClick={onClick}
        style={{
          width: '100%',
          textAlign: 'left',
          padding: '8px 10px',
          paddingRight: 32,
          borderRadius: 8,
          border: 'none',
          background: active ? token.colorFillSecondary : 'transparent',
          color: token.colorText,
          cursor: 'pointer',
          fontSize: 13,
          fontWeight: active ? 600 : 500,
          display: 'block',
          whiteSpace: 'nowrap',
          overflow: 'hidden',
          textOverflow: 'ellipsis',
          fontFamily: 'inherit',
        }}
      >
        {conversation.title}
      </button>
      {hovered ? (
        <Dropdown
          trigger={['click']}
          menu={{
            items: [
              {
                key: 'rename',
                label: 'Rename',
                icon: <Pencil size={12} strokeWidth={1.75} />,
                onClick: onRename,
              },
              {
                key: 'archive',
                label: 'Archive',
                icon: <Archive size={12} strokeWidth={1.75} />,
                danger: true,
                onClick: onArchive,
              },
            ],
          }}
        >
          <Tooltip title="More">
            <button
              type="button"
              aria-label="Conversation actions"
              style={{
                position: 'absolute',
                right: 4,
                top: 6,
                background: 'transparent',
                border: 'none',
                cursor: 'pointer',
                padding: 4,
                borderRadius: 4,
                color: token.colorTextSecondary,
              }}
            >
              <MoreHorizontal size={14} strokeWidth={1.75} />
            </button>
          </Tooltip>
        </Dropdown>
      ) : null}
    </div>
  );
}
