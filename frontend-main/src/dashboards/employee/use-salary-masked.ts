import { useCallback, useEffect, useState } from 'react';

const STORAGE_KEY = 'fshrms.dashboard.salary-masked';

function readInitial(): boolean {
  try {
    const raw = localStorage.getItem(STORAGE_KEY);
    if (raw === 'false') return false;
    return true;
  } catch {
    return true;
  }
}

export function useSalaryMasked(): [boolean, () => void] {
  const [masked, setMasked] = useState<boolean>(readInitial);

  useEffect(() => {
    try {
      localStorage.setItem(STORAGE_KEY, masked ? 'true' : 'false');
    } catch {
      // best-effort
    }
  }, [masked]);

  const toggle = useCallback(() => setMasked((m) => !m), []);

  return [masked, toggle];
}
