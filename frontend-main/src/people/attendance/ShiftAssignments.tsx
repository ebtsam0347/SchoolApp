import { Card, Tag, theme as antdTheme, Typography } from 'antd';
import { LayoutGrid } from 'lucide-react';

import { StatusPill } from '@/components';
import {
  formatDateShort,
  formatDateMonthDay,
  type EmployeeShiftDto,
} from '@/attendance';

const { Text } = Typography;
const { useToken } = antdTheme;

export type ShiftAssignmentsProps = {
  active: EmployeeShiftDto | null;
  history: EmployeeShiftDto[];
  isoToday: string;
};

export function ShiftAssignments({
  active,
  history,
  isoToday,
}: ShiftAssignmentsProps) {
  const { token } = useToken();

  return (
    <div style={{ display: 'flex', flexDirection: 'column', gap: 12 }}>
      {active ? (
        <Card
          styles={{ body: { padding: 16 } }}
          style={{
            borderColor: token.colorPrimary,
            boxShadow: `0 0 0 2px color-mix(in srgb, ${token.colorPrimary} 14%, transparent)`,
          }}
        >
          <ShiftRow shift={active} active />
        </Card>
      ) : (
        <Card styles={{ body: { padding: 16 } }}>
          <Text type="secondary">
            No shift covers today ({formatDateShort(isoToday)}). Either the
            assignment hasn't started yet, or the previous one has ended.
          </Text>
        </Card>
      )}

      {history.length > 0 ? (
        <div>
          <div
            style={{
              fontSize: 11,
              textTransform: 'uppercase',
              letterSpacing: '0.06em',
              color: token.colorTextTertiary,
              fontWeight: 600,
              padding: '8px 4px',
            }}
          >
            History
          </div>
          <div style={{ display: 'flex', flexDirection: 'column', gap: 8 }}>
            {history.map((s) => (
              <Card
                key={s.id}
                size="small"
                styles={{ body: { padding: 14, opacity: 0.85 } }}
              >
                <ShiftRow shift={s} />
              </Card>
            ))}
          </div>
        </div>
      ) : null}
    </div>
  );
}

function ShiftRow({
  shift,
  active = false,
}: {
  shift: EmployeeShiftDto;
  active?: boolean;
}) {
  const { token } = useToken();
  return (
    <div style={{ display: 'flex', alignItems: 'center', gap: 14 }}>
      <span
        aria-hidden
        style={{
          width: 36,
          height: 36,
          borderRadius: 10,
          background: active
            ? `color-mix(in srgb, ${token.colorPrimary} 14%, transparent)`
            : token.colorFillQuaternary,
          color: active ? token.colorPrimary : token.colorTextTertiary,
          display: 'inline-flex',
          alignItems: 'center',
          justifyContent: 'center',
          flexShrink: 0,
        }}
      >
        <LayoutGrid size={16} strokeWidth={1.75} />
      </span>
      <div style={{ flex: 1, minWidth: 0 }}>
        <div
          style={{
            display: 'flex',
            gap: 8,
            alignItems: 'center',
            fontWeight: 600,
            fontSize: 14,
          }}
        >
          {shift.shiftName}
          <Tag
            style={{
              margin: 0,
              fontFamily: 'ui-monospace, monospace',
              fontSize: 11,
            }}
          >
            {shift.shiftCode}
          </Tag>
          {active ? (
            <StatusPill tone="primary" size="sm">
              Active
            </StatusPill>
          ) : null}
        </div>
        <Text type="secondary" style={{ fontSize: 12 }}>
          {formatDateMonthDay(shift.effectiveFrom)} →{' '}
          {shift.effectiveTo ? formatDateMonthDay(shift.effectiveTo) : 'open'}
        </Text>
      </div>
    </div>
  );
}
