// Collapsible second-level list. Uses the grid-template-rows trick so the
// expand/collapse animates between 0fr ↔ 1fr without us needing to measure
// child height with JS.

import type { CSSProperties } from 'react';
import type { NavItem } from '../nav-config';
import { ActiveBar } from './ActiveBar';
import { NavBadgePill } from './NavBadge';

export type SubMenuProps = {
  items: NavItem[];
  activeKey: string;
  expanded: boolean;
  onSelect: (key: string) => void;
};

export function SubMenu({ items, activeKey, expanded, onSelect }: SubMenuProps) {
  const containerStyle: CSSProperties = {
    overflow: 'hidden',
    display: 'grid',
    gridTemplateRows: expanded ? '1fr' : '0fr',
    transition: 'grid-template-rows var(--app-transition-normal)',
  };

  return (
    <div style={containerStyle} aria-hidden={!expanded}>
      <ul
        style={{
          minHeight: 0,
          listStyle: 'none',
          margin: 0,
          padding: '2px 0 2px 24px',
          display: 'flex',
          flexDirection: 'column',
          gap: 2,
        }}
      >
        {items.map((child) => {
          const active = child.key === activeKey;
          return (
            <li key={child.key}>
              <button
                type="button"
                data-nav-row
                onClick={() => onSelect(child.key)}
                tabIndex={expanded ? 0 : -1}
                style={{
                  display: 'flex',
                  alignItems: 'center',
                  width: '100%',
                  height: 32,
                  padding: '0 10px',
                  borderRadius: 6,
                  border: 'none',
                  background: active ? 'var(--app-sidebar-bg-active)' : 'transparent',
                  color: active
                    ? 'var(--app-sidebar-text-active)'
                    : 'var(--app-sidebar-text-muted)',
                  fontSize: 12.5,
                  fontWeight: active ? 500 : 400,
                  cursor: 'pointer',
                  textAlign: 'left',
                  position: 'relative',
                  transition:
                    'background var(--app-transition-fast), color var(--app-transition-fast)',
                }}
                onMouseEnter={(e) => {
                  if (!active) {
                    e.currentTarget.style.background = 'var(--app-sidebar-bg-hover)';
                    e.currentTarget.style.color = 'var(--app-sidebar-text)';
                  }
                }}
                onMouseLeave={(e) => {
                  if (!active) {
                    e.currentTarget.style.background = 'transparent';
                    e.currentTarget.style.color = 'var(--app-sidebar-text-muted)';
                  }
                }}
              >
                {active && <ActiveBar />}
                <span
                  style={{
                    flex: 1,
                    minWidth: 0,
                    overflow: 'hidden',
                    textOverflow: 'ellipsis',
                    whiteSpace: 'nowrap',
                  }}
                >
                  {child.label}
                </span>
                {child.badge ? <NavBadgePill badge={child.badge} /> : null}
              </button>
            </li>
          );
        })}
      </ul>
    </div>
  );
}
