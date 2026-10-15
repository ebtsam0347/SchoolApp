// URL-search-param sync for "open detail drawer with id N" UX.
//
// Replaces the `const [openId, setOpenId] = useState<number | null>(null)`
// pattern when the open state should be bookmarkable / back-button-aware.
// Use it on every list page that opens a detail drawer.
//
// Usage (in a route component — the route must declare `validateSearch`
// with the matching key):
//
//   const [detailId, setDetailId] = useUrlDrawerId(
//     '/_app/time/leaves',
//     'detail',
//   );
//
//   <DetailDrawer open={detailId != null} onClose={() => setDetailId(null)} … />

import {
  useNavigate,
  useSearch,
  type RegisteredRouter,
  type RouteIds,
} from '@tanstack/react-router';
import { useCallback } from 'react';

type RouteId = RouteIds<RegisteredRouter['routeTree']>;

export function useUrlDrawerId<Key extends string>(
  routeFrom: RouteId,
  key: Key,
): [number | null, (next: number | null) => void] {
  const search = useSearch({ from: routeFrom }) as Record<string, unknown>;
  const navigate = useNavigate();
  const raw = search[key];
  const id =
    typeof raw === 'number'
      ? raw
      : typeof raw === 'string' && raw.length > 0
        ? Number(raw) || null
        : null;

  const setId = useCallback(
    (next: number | null) => {
      void navigate({
        search: ((s: Record<string, unknown>) => ({
          ...s,
          [key]: next ?? undefined,
        })) as never,
        replace: true,
      });
    },
    [navigate, key],
  );

  return [id, setId];
}
