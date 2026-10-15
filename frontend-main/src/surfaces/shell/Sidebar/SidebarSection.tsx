// A nav-group section in the expanded sidebar. The grey header doubles as a
// toggle: click it to fold the section's rows away. Uses the same
// grid-template-rows 0fr↔1fr trick as SubMenu so it animates without measuring
// height in JS. Collapse state is persisted per group key (sidebar-sections-store).
//
// Only rendered in the expanded sidebar — the collapsed rail hides group
// headers entirely (Sidebar/index.tsx), so there is nothing to fold there.
//
// `inert` removes folded rows from focus + the a11y tree; the
// `data-section-collapsed` marker lets Sidebar's ArrowUp/Down handler skip
// them too (it focuses rows programmatically, which `inert` alone would just
// swallow, stranding the caret).

import { useEffect, type CSSProperties } from 'react';
import { ChevronDown } from 'lucide-react';
import type { NavGroup, NavItem } from '../nav-config';
import { useSidebarSectionsStore } from '../sidebar-sections-store';
import { SidebarRow } from './SidebarRow';
import { isItemActive } from './nav-ability';

export type SidebarSectionProps = {
  group: NavGroup;
  activeKey: string;
  onSelect: (key: string) => void;
};

export function SidebarSection({ group, activeKey, onSelect }: SidebarSectionProps) {
  const collapsed = useSidebarSectionsStore((s) => s.collapsed.has(group.key));
  const toggle = useSidebarSectionsStore((s) => s.toggle);
  const expand = useSidebarSectionsStore((s) => s.expand);

  // Auto-unfold the section holding the active route on arrival — mirrors
  // SidebarRow's containsActive behaviour, so the active row is never hidden
  // behind a fold after a deep-link / palette / breadcrumb jump. Sibling
  // sections are left exactly as the user arranged them (no exclusive close).
  // Depends only on containsActive: folding this section manually while you're
  // inside it doesn't re-fire the effect, so the fold sticks.
  const containsActive = group.items.some((item) => isItemActive(item, activeKey));
  useEffect(() => {
    if (containsActive) expand(group.key);
  }, [containsActive, group.key, expand]);

  const listWrapStyle: CSSProperties = {
    overflow: 'hidden',
    display: 'grid',
    gridTemplateRows: collapsed ? '0fr' : '1fr',
    transition: 'grid-template-rows var(--app-transition-normal)',
  };

  return (
    <div style={{ marginBottom: 16 }}>
      <button
        type="button"
        onClick={() => toggle(group.key)}
        aria-expanded={!collapsed}
        style={{
          display: 'flex',
          alignItems: 'center',
          gap: 6,
          width: '100%',
          border: 'none',
          background: 'transparent',
          cursor: 'pointer',
          fontSize: 11,
          fontWeight: 600,
          letterSpacing: '0.08em',
          textTransform: 'uppercase',
          color: 'var(--app-sidebar-text-muted)',
          padding: '6px 10px',
          textAlign: 'left',
        }}
      >
        <span
          style={{
            flex: 1,
            minWidth: 0,
            overflow: 'hidden',
            textOverflow: 'ellipsis',
            whiteSpace: 'nowrap',
          }}
        >
          {group.label}
        </span>
        <ChevronDown
          size={13}
          style={{
            flexShrink: 0,
            transition: 'transform var(--app-transition-normal)',
            transform: collapsed ? 'rotate(-90deg)' : 'none',
            opacity: 0.6,
          }}
        />
      </button>
      <div
        style={listWrapStyle}
        aria-hidden={collapsed}
        data-section-collapsed={collapsed ? 'true' : undefined}
        inert={collapsed}
      >
        <ul
          style={{
            minHeight: 0,
            listStyle: 'none',
            margin: 0,
            padding: 0,
            display: 'flex',
            flexDirection: 'column',
            gap: 2,
          }}
        >
          {group.items.map((item: NavItem) => (
            <SidebarRow
              key={item.key}
              item={item}
              collapsed={false}
              activeKey={activeKey}
              onSelect={onSelect}
            />
          ))}
        </ul>
      </div>
    </div>
  );
}
