import { Tag, Typography } from 'antd';

import { OVERTIME_TYPE_COLOR, OVERTIME_TYPE_LABEL, formatMinutes } from '../format';
import type { OvertimeMineRow } from '../bff-api';
import type { OvertimeType } from '../types';

const { Text } = Typography;

const TYPE_MINUTES: Record<OvertimeType, keyof OvertimeMineRow> = {
  NORMAL: 'otNormalMinutes',
  EXTRA: 'otExtraMinutes',
  SPECIAL: 'otSpecialMinutes',
};

export function OtMinuteCell({ row }: { row: OvertimeMineRow }) {
  const type = row.request.type;
  const paid = row[TYPE_MINUTES[type]] as number;

  if (row.request.status !== 'APPROVED' || paid === 0) {
    return (
      <Text type="secondary" style={{ fontSize: 12 }}>
        {row.request.status === 'APPROVED' ? '0m' : 'derived after approval'}
      </Text>
    );
  }

  return (
    <span
      style={{
        display: 'inline-flex',
        alignItems: 'center',
        gap: 6,
        fontSize: 12,
      }}
    >
      <strong style={{ fontVariantNumeric: 'tabular-nums' }}>
        {formatMinutes(paid)}
      </strong>
      <Tag color={OVERTIME_TYPE_COLOR[type]} style={{ margin: 0 }}>
        {OVERTIME_TYPE_LABEL[type]}
      </Tag>
    </span>
  );
}
