// 7-column month calendar. Renders weekday header + 6 weeks of DayTiles.
// Loading state dims the grid rather than swapping to a skeleton so the
// month-to-month nav doesn't flash.

import { theme as antdTheme } from 'antd';
import { DayTile } from './DayTile';
import type { DayCell } from './types';

const { useToken } = antdTheme;

const WEEKDAY_HEADER = ['Sun', 'Mon', 'Tue', 'Wed', 'Thu', 'Fri', 'Sat'];

export type MonthGridProps = {
  days: DayCell[];
  onSelect: (workDate: string) => void;
  loading: boolean;
};

export function MonthGrid({ days, onSelect, loading }: MonthGridProps) {
  const { token } = useToken();

  return (
    <div
      style={{
        display: 'flex',
        flexDirection: 'column',
        gap: 8,
        opacity: loading ? 0.6 : 1,
        transition: 'opacity var(--app-transition-normal)',
      }}
    >
      <div style={{ display: 'grid', gridTemplateColumns: 'repeat(7, 1fr)', gap: 8 }}>
        {WEEKDAY_HEADER.map((d) => (
          <div
            key={d}
            style={{
              fontSize: 11,
              textTransform: 'uppercase',
              letterSpacing: '0.06em',
              color: token.colorTextTertiary,
              fontWeight: 600,
              textAlign: 'left',
              padding: '0 8px',
            }}
          >
            {d}
          </div>
        ))}
      </div>
      <div style={{ display: 'grid', gridTemplateColumns: 'repeat(7, 1fr)', gap: 8 }}>
        {days.map((d) => (
          <DayTile key={d.workDate} day={d} onClick={() => onSelect(d.workDate)} />
        ))}
      </div>
    </div>
  );
}
