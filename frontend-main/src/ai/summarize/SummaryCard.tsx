// One summary tile in the gallery. Click "Generate" to stream the body
// in token-by-token, or jump straight to the cached version. Mirrors
// what the Summarize button on people / audit pages will look like
// once we wire it for real.

import { Button, Card, Space, Tag, Typography, theme as antdTheme } from 'antd';
import { History, RefreshCw, ScrollText, Users } from 'lucide-react';
import { useRef, useState } from 'react';

import { Citations } from '../chat/Citations';
import { TypewriterText } from '../chat/TypewriterText';
import type { SummaryFixture } from '../mock/data';
import { mockStream } from '../mock/stream';

const { Text, Title } = Typography;
const { useToken } = antdTheme;

const KIND_META: Record<
  SummaryFixture['kind'],
  { label: string; icon: typeof History; tone: string }
> = {
  EMPLOYEE_TIMELINE: { label: 'Employee timeline', icon: History, tone: 'blue' },
  AUDIT_BY_ACTOR: { label: 'Audit by actor', icon: ScrollText, tone: 'purple' },
  TEAM_PULSE: { label: 'Team pulse', icon: Users, tone: 'green' },
};

interface SummaryCardProps {
  summary: SummaryFixture;
}

export function SummaryCard({ summary }: SummaryCardProps) {
  const { token } = useToken();
  const meta = KIND_META[summary.kind];
  const Icon = meta.icon;

  const [body, setBody] = useState<string>('');
  const [streaming, setStreaming] = useState(false);
  const [revealed, setRevealed] = useState(false);
  const abortRef = useRef<AbortController | null>(null);

  const generate = async () => {
    abortRef.current?.abort();
    const controller = new AbortController();
    abortRef.current = controller;

    setBody('');
    setStreaming(true);
    setRevealed(true);

    let assembled = '';
    try {
      for await (const ev of mockStream(summary.body, {
        signal: controller.signal,
        citations: summary.citations,
      })) {
        if (ev.type === 'delta') {
          assembled += ev.text;
          setBody(assembled);
        } else if (ev.type === 'done') {
          setStreaming(false);
        }
      }
    } catch {
      setStreaming(false);
    }
  };

  return (
    <Card
      styles={{
        body: { padding: 18, display: 'flex', flexDirection: 'column', gap: 12 },
      }}
    >
      <Space size={8} align="center">
        <Icon size={14} strokeWidth={1.75} color={token.colorPrimary} />
        <Tag color={meta.tone} style={{ margin: 0, fontSize: 11 }}>
          {meta.label}
        </Tag>
        <Text type="secondary" style={{ fontSize: 12 }}>
          {summary.rangeLabel}
        </Text>
      </Space>

      <div>
        <Title level={5} style={{ margin: 0 }}>
          {summary.subjectName}
        </Title>
        <Text type="secondary" style={{ fontSize: 12 }}>
          {summary.subjectMeta}
        </Text>
      </div>

      {!revealed ? (
        <div
          style={{
            padding: 14,
            borderRadius: 8,
            background: token.colorFillTertiary,
            border: `1px dashed ${token.colorBorderSecondary}`,
            textAlign: 'center',
          }}
        >
          <Text type="secondary" style={{ fontSize: 12, display: 'block', marginBottom: 8 }}>
            Press generate to stream the summary token-by-token, the same way the live
            ai-service will deliver it over SSE.
          </Text>
          <Button
            type="primary"
            icon={<RefreshCw size={14} strokeWidth={1.75} />}
            onClick={generate}
          >
            Generate summary
          </Button>
        </div>
      ) : (
        <div
          style={{
            padding: 14,
            borderRadius: 8,
            background: token.colorFillTertiary,
            fontSize: 13,
            lineHeight: 1.6,
          }}
        >
          <TypewriterText text={body} streaming={streaming} />
        </div>
      )}

      {revealed && !streaming ? <Citations citations={summary.citations} /> : null}

      {revealed && !streaming ? (
        <Button
          type="text"
          size="small"
          icon={<RefreshCw size={12} strokeWidth={1.75} />}
          onClick={generate}
          style={{ alignSelf: 'flex-start' }}
        >
          Regenerate
        </Button>
      ) : null}
    </Card>
  );
}
