// HR / ADMIN compliance reader — see any user's transcripts. Pure read,
// no editing. Mirrors the ai.admin.read permission on the live service:
// HR needs this when investigating a complaint or auditing how the
// assistant is being used.

import {
  Avatar,
  Card,
  Col,
  Input,
  Row,
  Space,
  Tag,
  Typography,
  theme as antdTheme,
} from 'antd';
import { Bot, MessageSquare, Search, ShieldCheck, User } from 'lucide-react';
import { useState } from 'react';

import { EmptyState, KpiGrid, PageHeader, Section, StatCard } from '@/components';
import { formatDateTime } from '@/lib/dates';

import { PreviewNotice } from '../PreviewNotice';
import { MessageBubble } from '../chat/MessageBubble';
import { mockStore } from '../mock/store';

const { Text } = Typography;
const { useToken } = antdTheme;

export function AdminConversations() {
  const { token } = useToken();
  const [search, setSearch] = useState('');
  const [activeId, setActiveId] = useState<string | null>(null);

  const all = mockStore.listAllConversations();

  const filtered = search.trim()
    ? all.filter((c) => {
        const q = search.toLowerCase();
        return (
          c.userName.toLowerCase().includes(q) ||
          c.title.toLowerCase().includes(q) ||
          c.messages.some((m) => m.body.toLowerCase().includes(q))
        );
      })
    : all;

  const active = activeId ? all.find((c) => c.id === activeId) ?? null : filtered[0] ?? null;

  return (
    <Space orientation="vertical" size={24} style={{ width: '100%' }}>
      <PageHeader
        eyebrow="ASSISTANT · ADMIN"
        title="All transcripts"
        description="Compliance-only view of every user's conversations. Read access is gated to HR and ADMIN; this list mirrors the ai-service /admin/conversations endpoint."
      />

      <PreviewNotice />

      <KpiGrid minColumnWidth={220}>
        <StatCard
          variant="accent-corner"
          tone="primary"
          icon={MessageSquare}
          label="Conversations"
          value={all.length.toString()}
          delta="across all users"
        />
        <StatCard
          variant="accent-corner"
          tone="info"
          icon={User}
          label="Active users"
          value={new Set(all.map((c) => c.userId)).size.toString()}
          delta="last 7 days"
        />
        <StatCard
          variant="accent-corner"
          tone="success"
          icon={Bot}
          label="Total messages"
          value={all.reduce((n, c) => n + c.messages.length, 0).toString()}
          delta="user + assistant"
        />
        <StatCard
          variant="accent-corner"
          tone="warning"
          icon={ShieldCheck}
          label="Read scope"
          value="ai.admin.read"
          delta="HR + ADMIN only"
        />
      </KpiGrid>

      <Row gutter={[16, 16]}>
        <Col xs={24} lg={9}>
          <Card
            styles={{
              body: { padding: 0, display: 'flex', flexDirection: 'column', height: 600 },
            }}
          >
            <div style={{ padding: 12, borderBottom: `1px solid ${token.colorBorderSecondary}` }}>
              <Input
                placeholder="Search by user, title, or content"
                prefix={<Search size={14} strokeWidth={1.75} />}
                value={search}
                onChange={(e) => setSearch(e.target.value)}
                allowClear
              />
            </div>
            <div style={{ flex: 1, overflowY: 'auto' }}>
              {filtered.length === 0 ? (
                <div style={{ padding: 24 }}>
                  <EmptyState
                    title="No transcripts match"
                    description="Clear the filter or try a broader phrase."
                    size="md"
                  />
                </div>
              ) : (
                filtered.map((c) => {
                  const selected = active?.id === c.id;
                  return (
                    <button
                      key={c.id}
                      type="button"
                      onClick={() => setActiveId(c.id)}
                      style={{
                        width: '100%',
                        textAlign: 'left',
                        padding: '12px 14px',
                        background: selected ? token.colorFillSecondary : 'transparent',
                        border: 'none',
                        borderBottom: `1px solid ${token.colorBorderSecondary}`,
                        cursor: 'pointer',
                        display: 'flex',
                        flexDirection: 'column',
                        gap: 4,
                        fontFamily: 'inherit',
                        color: token.colorText,
                      }}
                    >
                      <div
                        style={{
                          display: 'flex',
                          gap: 8,
                          alignItems: 'center',
                          justifyContent: 'space-between',
                        }}
                      >
                        <Space size={6}>
                          <Avatar
                            size={20}
                            icon={<User size={11} strokeWidth={1.75} />}
                            style={{
                              background: token.colorFillTertiary,
                              color: token.colorText,
                            }}
                          />
                          <Text strong style={{ fontSize: 13 }}>
                            {c.userName}
                          </Text>
                        </Space>
                        <Tag style={{ margin: 0, fontSize: 10 }}>{c.messages.length}</Tag>
                      </div>
                      <Text style={{ fontSize: 13 }} ellipsis>
                        {c.title}
                      </Text>
                      <Text type="secondary" style={{ fontSize: 11 }}>
                        {formatDateTime(c.updatedAt)}
                      </Text>
                    </button>
                  );
                })
              )}
            </div>
          </Card>
        </Col>

        <Col xs={24} lg={15}>
          <Card
            styles={{
              body: { padding: 0, display: 'flex', flexDirection: 'column', height: 600 },
            }}
          >
            {active ? (
              <>
                <div
                  style={{
                    padding: '14px 18px',
                    borderBottom: `1px solid ${token.colorBorderSecondary}`,
                    display: 'flex',
                    flexDirection: 'column',
                    gap: 4,
                  }}
                >
                  <Text strong style={{ fontSize: 14 }}>
                    {active.title}
                  </Text>
                  <Text type="secondary" style={{ fontSize: 12 }}>
                    {active.userName} · {formatDateTime(active.updatedAt)} ·{' '}
                    {active.messages.length} messages
                  </Text>
                </div>
                <div
                  style={{
                    flex: 1,
                    overflowY: 'auto',
                    padding: '18px 20px',
                    display: 'flex',
                    flexDirection: 'column',
                    gap: 16,
                  }}
                >
                  {active.messages.map((m) => (
                    <MessageBubble key={m.id} message={m} authorName={active.userName} />
                  ))}
                </div>
              </>
            ) : (
              <div style={{ padding: 32 }}>
                <EmptyState
                  title="Select a conversation"
                  description="Pick any row on the left to read the full transcript."
                  size="md"
                />
              </div>
            )}
          </Card>
        </Col>
      </Row>

      <Section title="Compliance posture">
        <Card
          styles={{
            body: { padding: 18, display: 'flex', flexDirection: 'column', gap: 6 },
          }}
        >
          <Text style={{ fontSize: 13, lineHeight: 1.6 }}>
            Bodies of every prompt and response are stored in the assistant
            database; the audit topic carries metadata only ({''}
            <Text code>userId, conversationId, model, latencyMs</Text>) so
            full content never leaves the assistant service. HR reads bodies
            via this admin view, which logs an audit event of its own.
          </Text>
          <Text type="secondary" style={{ fontSize: 12 }}>
            All access is identity-forwarded — there is no service account that
            can bypass it.
          </Text>
        </Card>
      </Section>
    </Space>
  );
}
