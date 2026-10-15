// Shown when a chat conversation has no messages yet — either a brand-new
// conversation, or the "no conversation selected" state. Suggests four
// canned prompts the user can click to seed the composer.

import { Button, Card, Typography, theme as antdTheme } from 'antd';
import { Bot, Sparkles } from 'lucide-react';

import { suggestedPrompts } from '../mock/data';

const { Title, Text } = Typography;
const { useToken } = antdTheme;

interface EmptyChatProps {
  onPick: (prompt: string) => void;
}

export function EmptyChat({ onPick }: EmptyChatProps) {
  const { token } = useToken();

  return (
    <div
      style={{
        flex: 1,
        display: 'flex',
        flexDirection: 'column',
        alignItems: 'center',
        justifyContent: 'center',
        padding: '32px 24px',
        gap: 24,
      }}
    >
      <div
        style={{
          width: 64,
          height: 64,
          borderRadius: 16,
          background: `linear-gradient(135deg, var(--app-accent-grad-from), var(--app-accent-grad-to))`,
          display: 'flex',
          alignItems: 'center',
          justifyContent: 'center',
          color: token.colorTextLightSolid,
          boxShadow: '0 8px 24px -8px rgba(0,0,0,0.25)',
        }}
      >
        <Bot size={32} strokeWidth={1.75} />
      </div>
      <div style={{ textAlign: 'center', maxWidth: 520 }}>
        <Title level={4} style={{ margin: 0 }}>
          How can I help with HR today?
        </Title>
        <Text type="secondary" style={{ fontSize: 13 }}>
          I can answer policy questions, find people in the directory, and summarise an employee's
          timeline or recent audit activity. Pick a starter or ask your own.
        </Text>
      </div>
      <div
        style={{
          display: 'grid',
          gridTemplateColumns: 'repeat(auto-fit, minmax(240px, 1fr))',
          gap: 10,
          width: '100%',
          maxWidth: 720,
        }}
      >
        {suggestedPrompts.map((p) => (
          <Card
            key={p.label}
            size="small"
            hoverable
            onClick={() => onPick(p.body)}
            styles={{ body: { padding: 12, display: 'flex', gap: 10, alignItems: 'flex-start' } }}
          >
            <Sparkles
              size={14}
              strokeWidth={1.75}
              color={token.colorPrimary}
              style={{ marginTop: 2, flexShrink: 0 }}
            />
            <div>
              <Text strong style={{ fontSize: 13, display: 'block' }}>
                {p.label}
              </Text>
              <Text type="secondary" style={{ fontSize: 12 }}>
                {p.body}
              </Text>
            </div>
          </Card>
        ))}
      </div>
      <Button type="text" size="small" onClick={() => onPick('')}>
        Or start with a blank prompt
      </Button>
    </div>
  );
}
