// Single-shot policy Q&A. No conversation state — type a question, get
// a streamed answer with citations, ask another. Useful surface for
// "I just want to look something up" — no transcript history clutter.

import { Button, Card, Input, Space, Typography, theme as antdTheme } from 'antd';
import { ArrowUp, BookOpen, Sparkles } from 'lucide-react';
import { useRef, useState } from 'react';

import { PageHeader, Section } from '@/components';

import { PreviewNotice } from '../PreviewNotice';
import { Citations } from '../chat/Citations';
import { TypewriterText } from '../chat/TypewriterText';
import { pickReply, suggestedPrompts, type Citation } from '../mock/data';
import { mockStream } from '../mock/stream';

const { Title, Text } = Typography;
const { useToken } = antdTheme;
const { TextArea } = Input;

interface AnswerState {
  question: string;
  body: string;
  citations: Citation[];
  streaming: boolean;
}

const POLICY_PROMPTS = suggestedPrompts.filter(
  (p) => /policy|leave|overtime|probation|disciplinary|per-diem|travel/i.test(p.body),
);

export function PolicyAsk() {
  const { token } = useToken();
  const [value, setValue] = useState('');
  const [answer, setAnswer] = useState<AnswerState | null>(null);
  const abortRef = useRef<AbortController | null>(null);

  const ask = async () => {
    const question = value.trim();
    if (!question) return;
    abortRef.current?.abort();

    const controller = new AbortController();
    abortRef.current = controller;

    const reply = pickReply(question);
    setAnswer({ question, body: '', citations: [], streaming: true });
    setValue('');

    let assembled = '';
    try {
      for await (const ev of mockStream(reply.body, {
        signal: controller.signal,
        citations: reply.citations,
      })) {
        if (ev.type === 'delta') {
          assembled += ev.text;
          setAnswer((prev) =>
            prev && prev.question === question ? { ...prev, body: assembled } : prev,
          );
        } else if (ev.type === 'done') {
          setAnswer((prev) =>
            prev && prev.question === question
              ? { ...prev, body: assembled, citations: ev.citations ?? [], streaming: false }
              : prev,
          );
        }
      }
    } catch {
      setAnswer((prev) => (prev ? { ...prev, streaming: false } : prev));
    }
  };

  return (
    <Space orientation="vertical" size={24} style={{ width: '100%' }}>
      <PageHeader
        eyebrow="ASSISTANT · POLICY Q&A"
        title="Ask the policy library"
        description="Type a single question and get an answer drawn from school policy documents, with citations to the exact clause. No conversation state — each question stands alone."
      />

      <PreviewNotice />

      <Card
        styles={{
          body: { padding: 18, display: 'flex', flexDirection: 'column', gap: 12 },
        }}
      >
        <TextArea
          autoFocus
          autoSize={{ minRows: 2, maxRows: 5 }}
          placeholder="e.g. How many days of annual leave do I get after confirmation?"
          value={value}
          onChange={(e) => setValue(e.target.value)}
          onPressEnter={(e) => {
            if (!e.shiftKey) {
              e.preventDefault();
              ask();
            }
          }}
        />
        <div
          style={{
            display: 'flex',
            justifyContent: 'space-between',
            alignItems: 'center',
            gap: 12,
          }}
        >
          <Text type="secondary" style={{ fontSize: 12 }}>
            Enter sends · Shift+Enter for newline
          </Text>
          <Button
            type="primary"
            icon={<ArrowUp size={14} strokeWidth={1.75} />}
            disabled={!value.trim()}
            onClick={ask}
          >
            Ask
          </Button>
        </div>
      </Card>

      {answer ? (
        <Section title="Answer">
          <Card
            styles={{
              body: { padding: 20, display: 'flex', flexDirection: 'column', gap: 14 },
            }}
          >
            <div
              style={{
                display: 'flex',
                gap: 8,
                alignItems: 'flex-start',
                paddingBottom: 10,
                borderBottom: `1px solid ${token.colorBorderSecondary}`,
              }}
            >
              <BookOpen
                size={14}
                strokeWidth={1.75}
                color={token.colorPrimary}
                style={{ marginTop: 3, flexShrink: 0 }}
              />
              <Text strong style={{ fontSize: 14 }}>
                {answer.question}
              </Text>
            </div>
            <div style={{ fontSize: 14, lineHeight: 1.6 }}>
              <TypewriterText text={answer.body} streaming={answer.streaming} />
            </div>
            {!answer.streaming && answer.citations.length > 0 ? (
              <Citations citations={answer.citations} />
            ) : null}
          </Card>
        </Section>
      ) : (
        <Section title="Suggested questions" subtitle="Click one to try it.">
          <div
            style={{
              display: 'grid',
              gridTemplateColumns: 'repeat(auto-fit, minmax(260px, 1fr))',
              gap: 10,
            }}
          >
            {POLICY_PROMPTS.map((p) => (
              <Card
                key={p.label}
                size="small"
                hoverable
                onClick={() => setValue(p.body)}
                styles={{ body: { padding: 12, display: 'flex', gap: 10, alignItems: 'flex-start' } }}
              >
                <Sparkles
                  size={14}
                  strokeWidth={1.75}
                  color={token.colorPrimary}
                  style={{ marginTop: 2, flexShrink: 0 }}
                />
                <div>
                  <Title level={5} style={{ margin: 0, fontSize: 13 }}>
                    {p.label}
                  </Title>
                  <Text type="secondary" style={{ fontSize: 12 }}>
                    {p.body}
                  </Text>
                </div>
              </Card>
            ))}
          </div>
        </Section>
      )}
    </Space>
  );
}
