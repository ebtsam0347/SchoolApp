// Citation chips rendered under an assistant message after the `done`
// event lands. Click a chip to open a popover with the snippet — same
// pattern the BFF will expose when the server finishes streaming.

import { Popover, theme as antdTheme, Typography } from 'antd';
import { BookOpen, History, ScrollText, User } from 'lucide-react';

import type { Citation } from '../mock/data';

const { Text } = Typography;
const { useToken } = antdTheme;

const ICONS: Record<Citation['sourceType'], typeof BookOpen> = {
  POLICY: BookOpen,
  EMPLOYEE: User,
  TIMELINE: History,
  AUDIT: ScrollText,
};

interface CitationsProps {
  citations: Citation[];
}

export function Citations({ citations }: CitationsProps) {
  if (citations.length === 0) return null;

  return (
    <div
      style={{
        display: 'flex',
        flexWrap: 'wrap',
        gap: 6,
        marginTop: 10,
      }}
    >
      {citations.map((c, i) => (
        <CitationChip key={`${c.sourceId}-${i}`} citation={c} index={i + 1} />
      ))}
    </div>
  );
}

function CitationChip({ citation, index }: { citation: Citation; index: number }) {
  const { token } = useToken();
  const Icon = ICONS[citation.sourceType];

  return (
    <Popover
      placement="topLeft"
      content={
        <div style={{ maxWidth: 360 }}>
          <Text strong style={{ fontSize: 13, display: 'block', marginBottom: 6 }}>
            {citation.title}
          </Text>
          <Text type="secondary" style={{ fontSize: 12 }}>
            {citation.snippet}
          </Text>
        </div>
      }
    >
      <button
        type="button"
        style={{
          display: 'inline-flex',
          alignItems: 'center',
          gap: 6,
          padding: '3px 8px',
          fontSize: 11,
          fontWeight: 500,
          borderRadius: 999,
          border: `1px solid ${token.colorBorderSecondary}`,
          background: token.colorFillTertiary,
          color: token.colorTextSecondary,
          cursor: 'pointer',
          fontFamily: 'inherit',
        }}
      >
        <span
          style={{
            display: 'inline-flex',
            alignItems: 'center',
            justifyContent: 'center',
            width: 16,
            height: 16,
            borderRadius: '50%',
            background: token.colorPrimary,
            color: token.colorTextLightSolid,
            fontSize: 10,
            fontWeight: 700,
          }}
        >
          {index}
        </span>
        <Icon size={12} strokeWidth={1.75} />
        <span>{citation.title}</span>
      </button>
    </Popover>
  );
}
