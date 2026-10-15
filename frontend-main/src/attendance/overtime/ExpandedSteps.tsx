import { Card, Typography } from 'antd';

import { StatusPill } from '@/components';
import {
  APPROVAL_STEP_STATUS_LABEL,
  APPROVAL_STEP_STATUS_TONE,
} from '../format';
import type { OvertimeMineRow } from '../bff-api';

const { Text } = Typography;

export function ExpandedSteps({ row }: { row: OvertimeMineRow }) {
  return (
    <div
      style={{
        display: 'grid',
        gridTemplateColumns: 'repeat(auto-fit, minmax(220px, 1fr))',
        gap: 8,
      }}
    >
      {row.request.steps.map((s) => (
        <Card
          key={s.stageNo}
          size="small"
          styles={{ body: { padding: 12 } }}
          style={{ borderRadius: 10 }}
        >
          <div
            style={{
              display: 'flex',
              justifyContent: 'space-between',
              alignItems: 'center',
            }}
          >
            <strong style={{ fontSize: 12 }}>
              {s.stageNo}. {s.stageRole}
            </strong>
            <StatusPill tone={APPROVAL_STEP_STATUS_TONE[s.status]}>
              {APPROVAL_STEP_STATUS_LABEL[s.status]}
            </StatusPill>
          </div>
          <Text
            type="secondary"
            style={{ fontSize: 11, display: 'block', marginTop: 4 }}
          >
            {s.assignedToName ??
              (s.assignedTo ? `User #${s.assignedTo}` : `${s.stageRole} role`)}
          </Text>
          {s.note ? (
            <Text style={{ fontSize: 11, display: 'block', marginTop: 4 }}>
              {s.note}
            </Text>
          ) : null}
        </Card>
      ))}
    </div>
  );
}
