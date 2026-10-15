// A number that rolls smoothly from its previous value to the next whenever
// the `value` prop changes — so a live counter visibly "ticks up" (245 → 246)
// without a page refresh. Respects prefers-reduced-motion (snaps instantly).
//
// On an increase it briefly flashes green + pops, drawing the eye to the change
// (see .fs-count-* in dashboard-live.css).

import { useEffect, useRef, useState } from 'react';
import type { CSSProperties } from 'react';
import { useReducedMotion } from '@/components/charts';
import './dashboard-live.css';

export type AnimatedNumberProps = {
  value: number;
  /** Tween duration in ms. Default 600. */
  durationMs?: number;
  /** Format the displayed integer. Default `en-PK` grouping. */
  format?: (n: number) => string;
  className?: string;
  style?: CSSProperties;
};

const easeOutCubic = (t: number) => 1 - Math.pow(1 - t, 3);
const defaultFormat = (n: number) => n.toLocaleString('en-PK');

export function AnimatedNumber({
  value,
  durationMs = 600,
  format = defaultFormat,
  className,
  style,
}: AnimatedNumberProps) {
  const reduced = useReducedMotion();
  const [display, setDisplay] = useState(value);
  // Whether the last change was an increase — drives the flash/pop.
  const [bumpKey, setBumpKey] = useState(0);
  const [rising, setRising] = useState(false);

  // Track what we're currently showing so an interrupted tween resumes from
  // the on-screen value rather than snapping.
  const displayRef = useRef(value);
  displayRef.current = display;
  const prevValueRef = useRef(value);

  useEffect(() => {
    const to = value;
    const from = displayRef.current;
    if (to > prevValueRef.current) {
      setRising(true);
      setBumpKey((k) => k + 1);
    } else if (to < prevValueRef.current) {
      setRising(false);
    }
    prevValueRef.current = to;

    if (reduced || from === to) {
      setDisplay(to);
      return;
    }

    let raf = 0;
    const start = performance.now();
    const tick = (now: number) => {
      const t = Math.min(1, (now - start) / durationMs);
      setDisplay(Math.round(from + (to - from) * easeOutCubic(t)));
      if (t < 1) raf = requestAnimationFrame(tick);
    };
    raf = requestAnimationFrame(tick);
    return () => cancelAnimationFrame(raf);
  }, [value, durationMs, reduced]);

  return (
    <span
      key={bumpKey}
      className={`${rising ? 'fs-count-up' : ''}${className ? ` ${className}` : ''}`}
      style={{ fontVariantNumeric: 'tabular-nums', display: 'inline-block', ...style }}
    >
      {format(display)}
    </span>
  );
}
