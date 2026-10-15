// `/bff/search` client + TanStack Query hook for the command palette.
//
// Contract spec'd in BFF_SEARCH_WISHLIST.md (S0). The aggregator owns the
// type → URL mapping and visibility filtering, so the FE just renders hits.
//
// FE-side guardrails:
//   - `MIN_QUERY_LEN`: skip the request below this; matches the BFF rule and
//     keeps stray single-char keystrokes off the wire.
//   - `placeholderData: (prev) => prev`: result list doesn't flash empty
//     between keystrokes.
//   - `retry: false`: palette is keystroke-driven; failures are common
//     (cancelled, timed out) and self-heal on the next keystroke.
//   - `staleTime: 10_000`: aligned with the BFF `Cache-Control: max-age=10`.

import { useQuery } from '@tanstack/react-query';
import {
  Briefcase,
  CalendarCheck,
  GraduationCap,
  Hash,
  Inbox,
  Map as MapIcon,
  Network,
  Plane,
  Receipt,
  Sparkles,
  Tags,
  User,
  type LucideIcon,
} from 'lucide-react';
import { http } from '@/lib/http';

export interface SearchHit {
  id: string;
  type: string;
  title: string;
  subtitle?: string;
  url: string;
  score: number;
  badge?: string;
}

export interface SearchProviderInfo {
  type: string;
  hits: number;
  ms: number;
  status: 'ok' | 'timeout' | 'error';
}

export interface SearchResponse {
  results: SearchHit[];
  partial: boolean;
  providers: SearchProviderInfo[];
}

export const MIN_QUERY_LEN = 2;

export function usePaletteSearch(debouncedQuery: string) {
  const q = debouncedQuery.trim();
  return useQuery({
    queryKey: ['palette', 'search', q],
    queryFn: ({ signal }) =>
      http.get<SearchResponse>(
        `/bff/search?q=${encodeURIComponent(q)}&limit=5`,
        { signal },
      ),
    enabled: q.length >= MIN_QUERY_LEN,
    staleTime: 10_000,
    placeholderData: (prev) => prev,
    retry: false,
  });
}

const TYPE_ICONS: Record<string, LucideIcon> = {
  employee: User,
  'payslip-mine': Receipt,
  'leave-mine': Plane,
  'lookup-department': Network,
  'lookup-section': Tags,
  'lookup-designation': Briefcase,
  'lookup-grade': Sparkles,
  'lookup-city': MapIcon,
  'lookup-skill': Sparkles,
  'lookup-qualification': GraduationCap,
  'org-node': Network,
  'inbox-item': Inbox,
  page: CalendarCheck,
};

export function iconForHit(hit: SearchHit): LucideIcon {
  return TYPE_ICONS[hit.type] ?? Hash;
}
