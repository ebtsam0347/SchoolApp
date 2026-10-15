// A split-flap "clock board" number. Each digit sits in its own dark tile and
// rolls vertically to its new value whenever the count changes — so a live
// tally visibly climbs (07 → 08 → 09) as attendance events stream in, exactly
// like a flip-clock / airport board. Right-aligned; grows a tile at a time.
// Honors prefers-reduced-motion (snaps to the value with no roll).

import { useMemo } from 'react';
import { useReducedMotion } from '@/components/charts';
import './dashboard-live.css';

export type FlipNumberProps = {
  value: number;
  /** Pad to at least this many digit tiles (leading zeros get their own tile). */
  minDigits?: number;
  /** Tile size in px (tiles scale to the font-size). Default inherits. */
  size?: number;
};

export function FlipNumber({ value, minDigits = 2, size }: FlipNumberProps) {
  const reduced = useReducedMotion();
  const safe = Math.max(0, Math.round(value || 0));
  const digits = useMemo(
    () => safe.toString().padStart(minDigits, '0').split(''),
    [safe, minDigits],
  );

  return (
    <span
      className="fs-flip"
      style={size ? { fontSize: size } : undefined}
      role="img"
      aria-label={safe.toLocaleString('en-PK')}
    >
      {digits.map((d, i) => (
        // Key by place value (distance from the right) so a tile keeps its
        // identity — and its roll — even as the number gains/loses a digit.
        <span className="fs-flip-tile" key={digits.length - i} aria-hidden>
          <span
            className={reduced ? 'fs-flip-reel fs-flip-reel-static' : 'fs-flip-reel'}
            style={{ transform: `translateY(${-Number(d) * 10}%)` }}
          >
            {Array.from({ length: 10 }, (_, n) => (
              <span className="fs-flip-cell" key={n}>
                {n}
              </span>
            ))}
          </span>
        </span>
      ))}
    </span>
  );
}