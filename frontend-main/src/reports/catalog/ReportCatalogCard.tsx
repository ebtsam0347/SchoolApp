// One catalog row. Renders title, description, format / mode pills, and
// a Run button. The card itself is hoverable; clicking anywhere on the
// surface (or the Run button explicitly) fires onRun.

import { Button, Card, Space, Typography, theme as antdTheme } from 'antd';
import { Play } from 'lucide-react';

import { StatusPill } from '@/components';

import type { ReportCatalogEntry } from '../types';
import {
  FORMAT_LABEL,
  FORMAT_TONE,
  MODE_LABEL,
  MODE_TONE,
} from './labels';

const { Title, Paragraph } = Typography;
const { useToken } = antdTheme;

export interface ReportCatalogCardProps {
  entry: ReportCatalogEntry;
  onRun: () => void;
  /** A direct download from this card is in flight. */
  running?: boolean;
}

export function ReportCatalogCard({ entry, onRun, running = false }: ReportCatalogCardProps) {
  const { token } = useToken();
  const paramCount = entry.parameters.length;

  return (
    <Card
      hoverable
      onClick={running ? undefined : onRun}
      styles={{
        body: {
          padding: 18,
          display: 'flex',
          flexDirection: 'column',
          gap: 10,
          height: '100%',
        },
      }}
      style={{ height: '100%' }}
    >
      <Space size={6} wrap>
        <StatusPill tone={FORMAT_TONE[entry.format]}>
          {FORMAT_LABEL[entry.format]}
        </StatusPill>
        <StatusPill tone={MODE_TONE[entry.mode]}>
          {MODE_LABEL[entry.mode]}
        </StatusPill>
      </Space>
      <Title level={5} style={{ margin: 0 }}>
        {entry.title}
      </Title>
      <Paragraph
        type="secondary"
        style={{ margin: 0, fontSize: 13, lineHeight: 1.5, flex: 1 }}
        ellipsis={{ rows: 3 }}
      >
        {entry.description}
      </Paragraph>
      <div
        style={{
          display: 'flex',
          alignItems: 'center',
          justifyContent: 'space-between',
          marginTop: 4,
        }}
      >
        <span style={{ fontSize: 12, color: token.colorTextTertiary }}>
          {paramCount === 0
            ? 'No parameters'
            : `${paramCount} parameter${paramCount === 1 ? '' : 's'}`}
        </span>
        <Button
          type="primary"
          size="small"
          icon={<Play size={12} strokeWidth={2} />}
          loading={running}
          disabled={running}
          onClick={(e) => {
            e.stopPropagation();
            onRun();
          }}
        >
          {entry.mode === 'sync' && paramCount === 0 ? 'Download' : 'Run'}
        </Button>
      </div>
    </Card>
  );
}
