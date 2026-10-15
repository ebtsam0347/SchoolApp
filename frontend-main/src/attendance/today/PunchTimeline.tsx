// Today's punches rendered as a vertical TimelineItem stack.

import { Tag, Typography } from 'antd';
import { LogIn, LogOut } from 'lucide-react';
import { TimelineItem } from '@/components';
import { PUNCH_SOURCE_LABEL, formatClock, type PunchDto } from '@/attendance';

const { Text } = Typography;

export function PunchTimeline({ punches }: { punches: PunchDto[] }) {
  const sorted = punches.slice().sort((a, b) => a.punchedAt.localeCompare(b.punchedAt));
  return (
    <div style={{ display: 'flex', flexDirection: 'column', gap: 0 }}>
      {sorted.map((p, i) => (
        <TimelineItem
          key={p.id}
          isLast={i === sorted.length - 1}
          tone={
            p.source === 'OPERATOR' || p.source === 'REGULARIZED' ? 'warning' : 'success'
          }
          icon={i % 2 === 0 ? LogIn : LogOut}
          title={
            <span style={{ display: 'inline-flex', alignItems: 'center', gap: 8 }}>
              <strong>{i % 2 === 0 ? 'Punched in' : 'Punched out'}</strong>
              <Text type="secondary" style={{ fontSize: 12 }}>
                {formatClock(p.punchedAt)}
              </Text>
              <Tag style={{ margin: 0, fontSize: 10 }}>{PUNCH_SOURCE_LABEL[p.source]}</Tag>
              {p.deviceId ? (
                <Text type="secondary" style={{ fontSize: 11 }}>
                  · {p.deviceId}
                </Text>
              ) : null}
            </span>
          }
          description={p.reason ?? undefined}
        />
      ))}
    </div>
  );
}
