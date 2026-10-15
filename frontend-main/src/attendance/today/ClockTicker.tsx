// 1Hz clock display used inside the punch hero card. Pure presentational —
// the parent decides where to place it.

import { useEffect, useState } from 'react';

export function ClockTicker() {
  const [now, setNow] = useState(() => new Date());
  useEffect(() => {
    const t = setInterval(() => setNow(new Date()), 1000);
    return () => clearInterval(t);
  }, []);
  return (
    <span style={{ fontVariantNumeric: 'tabular-nums' }}>
      {now.toLocaleTimeString('en-GB', {
        hour: '2-digit',
        minute: '2-digit',
        hour12: false,
      })}
    </span>
  );
}
