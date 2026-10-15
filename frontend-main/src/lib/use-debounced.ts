// "Hold off until input settles" hook. Used by anything that fires a
// query off a typing/dragging input — search boxes, the leave preview
// API, the delegation candidate picker.

import { useEffect, useState } from 'react';

export function useDebounced<T>(value: T, ms: number): T {
  const [debounced, setDebounced] = useState(value);
  useEffect(() => {
    const t = setTimeout(() => setDebounced(value), ms);
    return () => clearTimeout(t);
  }, [value, ms]);
  return debounced;
}
