import { theme as antdTheme } from 'antd';

import type { HolidayDto } from '../types';
import { HolidayCard } from './HolidayCard';

const { useToken } = antdTheme;

export type MonthGroupProps = {
  monthLabel: string;
  items: HolidayDto[];
};

export function MonthGroup({ monthLabel, items }: MonthGroupProps) {
  const { token } = useToken();
  return (
    <div>
      <div
        style={{
          fontSize: 11,
          textTransform: 'uppercase',
          letterSpacing: '0.08em',
          color: token.colorTextTertiary,
          fontWeight: 600,
          marginBottom: 8,
        }}
      >
        {monthLabel}
      </div>
      <div
        style={{
          display: 'grid',
          gridTemplateColumns: 'repeat(auto-fit, minmax(280px, 1fr))',
          gap: 10,
        }}
      >
        {items.map((h) => (
          <HolidayCard key={h.id} holiday={h} />
        ))}
      </div>
    </div>
  );
}
