import { useContext, useEffect, useState } from 'react';
import { Input, Modal, theme as antdTheme } from 'antd';
import { useQuery } from '@tanstack/react-query';
import { useNavigate } from '@tanstack/react-router';
import { Loader2, Search, type LucideIcon } from 'lucide-react';
import { AbilityContext, useAuth, type AppAbility } from '@/auth';
import { peopleBff } from '@/people';
import { useDebounced } from '@/lib';
import { KbdHint } from '@/components/KbdHint';
import {
  buildPaletteEntries,
  type PaletteEntry,
  type PaletteGroup,
} from './palette-entries';
import { type RecentEntry } from './palette-recents';
import { usePaletteStore } from './palette-store';
import {
  iconForHit,
  MIN_QUERY_LEN,
  usePaletteSearch,
  type SearchHit,
} from './palette-search';

const { useToken } = antdTheme;

const STATIC_GROUP_ORDER: PaletteGroup[] = ['Recent', 'Quick actions', 'Navigate'];

interface RenderEntry {
  uniqueKey: string;
  label: string;
  hint?: string;
  icon: LucideIcon;
  group: string;
  url: string;
  /** Set when the entry maps back to a `nav-config` / quick-action registry row. */
  staticKey?: string;
  /** Set for remote entries so we can snapshot them into recents on select. */
  remote?: RecentEntry & { kind: 'remote' };
}

interface CommandPaletteProps {
  open: boolean;
  onClose: () => void;
}

export function CommandPalette({ open, onClose }: CommandPaletteProps) {
  const { token } = useToken();
  const navigate = useNavigate();
  const ability = useContext(AbilityContext) as AppAbility;
  const { user } = useAuth();
  const userId = user?.userId ?? null;

  const [query, setQuery] = useState('');
  const [activeIndex, setActiveIndex] = useState(0);

  const recents = usePaletteStore((s) => s.recents);
  const syncRecents = usePaletteStore((s) => s.syncRecents);
  const pushRecentAction = usePaletteStore((s) => s.pushRecent);

  // Debounced copy drives the network call. Static filtering uses the raw
  // query so the local list updates per keystroke.
  const debouncedQuery = useDebounced(query, 250);
  const remote = usePaletteSearch(debouncedQuery);

  const employeeLinked = user?.employeeLinked === true;
  // Shares the cache with the Sidebar + useDashboardPersona via query key
  // + staleTime — no extra request per palette open.
  const profile = useQuery({
    queryKey: ['account', 'profile'],
    queryFn: ({ signal }) => peopleBff.accountProfile(signal),
    staleTime: 60_000,
    enabled: employeeLinked,
    retry: false,
  });
  const hasDirectReports =
    (profile.data?.employee?.directReportsCount ?? 0) > 0;

  const entries = buildPaletteEntries(ability, {
    employeeLinked,
    hasDirectReports,
    roles: user?.roles,
  });

  useEffect(() => {
    if (open) syncRecents(userId);
    else {
      setQuery('');
      setActiveIndex(0);
    }
  }, [open, userId, syncRecents]);

  useEffect(() => {
    setActiveIndex(0);
  }, [query]);

  const q = query.trim().toLowerCase();
  const isSearching = q.length >= MIN_QUERY_LEN;

  const visible: RenderEntry[] = (() => {
    if (!isSearching) {
      const byKey = new Map(entries.map((e) => [e.key, e]));
      const usedStatic = new Set<string>();
      const recentRendered: RenderEntry[] = [];

      for (const r of recents) {
        if (r.kind === 'static') {
          const e = byKey.get(r.key);
          if (!e) continue; // dropped from nav-config — silently skip
          usedStatic.add(e.key);
          recentRendered.push(staticToRender({ ...e, group: 'Recent' }));
        } else {
          recentRendered.push(remoteRecentToRender(r));
        }
      }

      const rest = entries
        .filter((e) => !usedStatic.has(e.key))
        .map(staticToRender);
      return [...recentRendered, ...rest];
    }

    const staticMatches = entries
      .filter((e) => e.label.toLowerCase().includes(q))
      .map(staticToRender);
    const remoteRendered = (remote.data?.results ?? []).map(remoteToRender);
    return [...staticMatches, ...remoteRendered];
  })();

  const grouped: ReadonlyArray<readonly [string, RenderEntry[]]> = (() => {
    const order: string[] = [];
    const map = new Map<string, RenderEntry[]>();
    for (const item of visible) {
      if (!map.has(item.group)) {
        order.push(item.group);
        map.set(item.group, []);
      }
      map.get(item.group)!.push(item);
    }
    if (!isSearching) {
      order.sort(
        (a, b) =>
          STATIC_GROUP_ORDER.indexOf(a as PaletteGroup) -
          STATIC_GROUP_ORDER.indexOf(b as PaletteGroup),
      );
    }
    return order.filter((g) => map.has(g)).map((g) => [g, map.get(g)!] as const);
  })();

  const select = (entry: RenderEntry) => {
    if (entry.staticKey) {
      pushRecentAction(userId, { kind: 'static', key: entry.staticKey });
    } else if (entry.remote) {
      pushRecentAction(userId, entry.remote);
    }
    onClose();
    void navigate({ to: entry.url as never });
  };

  const onKeyDown = (e: React.KeyboardEvent) => {
    if (e.key === 'ArrowDown') {
      e.preventDefault();
      setActiveIndex((i) => Math.min(i + 1, visible.length - 1));
    } else if (e.key === 'ArrowUp') {
      e.preventDefault();
      setActiveIndex((i) => Math.max(i - 1, 0));
    } else if (e.key === 'Enter' && visible.length > 0) {
      e.preventDefault();
      const target = visible[activeIndex] ?? visible[0];
      if (target) select(target);
    }
  };

  const showSpinner = isSearching && remote.isFetching;
  const showPartialNote = isSearching && remote.data?.partial === true;
  const showRemoteError = isSearching && !!remote.error;

  return (
    <Modal
      open={open}
      onCancel={onClose}
      footer={null}
      closable={false}
      destroyOnHidden
      width={620}
      styles={{ body: { padding: 0 } }}
    >
      <div
        onKeyDown={onKeyDown}
        style={{ padding: '14px 16px', borderBottom: `1px solid ${token.colorBorderSecondary}` }}
      >
        <Input
          autoFocus
          variant="borderless"
          size="large"
          placeholder="Search people, payslips, leaves, lookups…"
          prefix={
            showSpinner ? (
              <Loader2
                size={16}
                strokeWidth={1.75}
                color={token.colorTextTertiary}
                style={{ animation: 'spin 800ms linear infinite' }}
              />
            ) : (
              <Search size={16} strokeWidth={1.75} color={token.colorTextTertiary} />
            )
          }
          value={query}
          onChange={(e) => setQuery(e.target.value)}
          styles={{ input: { fontSize: 15 } }}
        />
        <style>{`@keyframes spin { to { transform: rotate(360deg); } }`}</style>
      </div>
      <div
        onKeyDown={onKeyDown}
        style={{ maxHeight: 420, overflowY: 'auto', padding: 8 }}
      >
        {visible.length === 0 && (
          <div
            style={{
              padding: '32px 12px',
              textAlign: 'center',
              color: token.colorTextTertiary,
              fontSize: 13,
            }}
          >
            {isSearching && remote.isFetching
              ? 'Searching…'
              : `No matches for "${query}"`}
          </div>
        )}
        {(() => {
          let runningIndex = 0;
          return grouped.map(([group, entriesInGroup]) => (
            <div key={group} style={{ marginBottom: 4 }}>
              <div
                style={{
                  fontSize: 11,
                  fontWeight: 600,
                  letterSpacing: '0.08em',
                  textTransform: 'uppercase',
                  color: token.colorTextTertiary,
                  padding: '8px 10px 4px',
                }}
              >
                {group}
              </div>
              <ul style={{ listStyle: 'none', margin: 0, padding: 0 }}>
                {entriesInGroup.map((entry) => {
                  const Icon = entry.icon;
                  const itemIndex = runningIndex++;
                  const isActive = itemIndex === activeIndex;
                  return (
                    <li key={entry.uniqueKey}>
                      <button
                        type="button"
                        onClick={() => select(entry)}
                        onMouseEnter={() => setActiveIndex(itemIndex)}
                        style={{
                          display: 'flex',
                          alignItems: 'center',
                          gap: 12,
                          width: '100%',
                          padding: '8px 10px',
                          borderRadius: 8,
                          border: 'none',
                          background: isActive ? token.colorFillTertiary : 'transparent',
                          cursor: 'pointer',
                          color: token.colorText,
                          textAlign: 'left',
                          transition: 'background 120ms',
                        }}
                      >
                        <Icon size={16} strokeWidth={1.75} color={token.colorTextSecondary} />
                        <span
                          style={{
                            flex: 1,
                            fontSize: 13,
                            whiteSpace: 'nowrap',
                            overflow: 'hidden',
                            textOverflow: 'ellipsis',
                          }}
                        >
                          {entry.label}
                        </span>
                        {entry.hint && (
                          <span style={{ fontSize: 11, color: token.colorTextTertiary }}>
                            {entry.hint}
                          </span>
                        )}
                      </button>
                    </li>
                  );
                })}
              </ul>
            </div>
          ));
        })()}
        {showPartialNote && (
          <div
            style={{
              padding: '8px 12px',
              fontSize: 11,
              color: token.colorTextTertiary,
              borderTop: `1px solid ${token.colorBorderSecondary}`,
              marginTop: 4,
            }}
          >
            Showing what's available — a few results may still be loading.
          </div>
        )}
        {showRemoteError && (
          <div
            style={{
              padding: '8px 12px',
              fontSize: 11,
              color: token.colorWarning,
              borderTop: `1px solid ${token.colorBorderSecondary}`,
              marginTop: 4,
            }}
          >
            Search is unavailable — showing local results only.
          </div>
        )}
      </div>
      <div
        style={{
          display: 'flex',
          alignItems: 'center',
          gap: 16,
          padding: '8px 14px',
          borderTop: `1px solid ${token.colorBorderSecondary}`,
          fontSize: 11,
          color: token.colorTextTertiary,
          background: token.colorFillQuaternary,
        }}
      >
        <PaletteHint label="Navigate" keys={['↑', '↓']} />
        <PaletteHint label="Open" keys="↵" />
        <PaletteHint label="Close" keys="esc" />
      </div>
    </Modal>
  );
}

function staticToRender(entry: PaletteEntry): RenderEntry {
  return {
    uniqueKey: entry.key,
    label: entry.label,
    hint: entry.hint,
    icon: entry.icon,
    group: entry.group,
    url: entry.to,
    staticKey: entry.key,
  };
}

function remoteToRender(hit: SearchHit): RenderEntry {
  return {
    uniqueKey: `remote-${hit.id}`,
    label: hit.subtitle ? `${hit.title} · ${hit.subtitle}` : hit.title,
    hint: hit.badge,
    icon: iconForHit(hit),
    group: hit.badge ?? hit.type,
    url: hit.url,
    remote: {
      kind: 'remote',
      id: hit.id,
      type: hit.type,
      title: hit.title,
      subtitle: hit.subtitle,
      url: hit.url,
      badge: hit.badge,
    },
  };
}

function remoteRecentToRender(
  recent: RecentEntry & { kind: 'remote' },
): RenderEntry {
  return {
    uniqueKey: `remote-${recent.id}`,
    label: recent.subtitle
      ? `${recent.title} · ${recent.subtitle}`
      : recent.title,
    hint: recent.badge,
    icon: iconForHit({
      id: recent.id,
      type: recent.type,
      title: recent.title,
      url: recent.url,
      score: 0,
    }),
    group: 'Recent',
    url: recent.url,
    remote: recent,
  };
}

function PaletteHint({ label, keys }: { label: string; keys: string | string[] }) {
  return (
    <span style={{ display: 'inline-flex', alignItems: 'center', gap: 6 }}>
      <KbdHint keys={keys} size="sm" />
      <span>{label}</span>
    </span>
  );
}
