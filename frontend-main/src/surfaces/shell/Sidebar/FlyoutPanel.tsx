// Popover content for collapsed-rail mode. When the user hovers a parent
// item with children, this panel appears to the right of the rail showing
// the second-level options (otherwise unreachable in collapsed state).

import type { NavItem } from '../nav-config';
import { NavBadgePill } from './NavBadge';

export type FlyoutPanelProps = {
  item: NavItem;
  activeKey: string;
  onSelect: (key: string) => void;
};

export function FlyoutPanel({ item, activeKey, onSelect }: FlyoutPanelProps) {
  return (
    <div style={{ minWidth: 200 }}>
      <div
        style={{
          fontSize: 11,
          fontWeight: 600,
          letterSpacing: '0.08em',
          textTransform: 'uppercase',
          color: 'var(--app-sidebar-text-muted)',
          padding: '6px 10px 4px',
        }}
      >
        {item.label}
      </div>
      <ul
        style={{
          listStyle: 'none',
          margin: 0,
          padding: 0,
          display: 'flex',
          flexDirection: 'column',
          gap: 2,
        }}
      >
        {item.children!.map((child) => {
          const active = child.key === activeKey;
          return (
            <li key={child.key}>
              <button
                type="button"
                onClick={() => onSelect(child.key)}
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
                    : 'var(--app-sidebar-text)',
                  fontSize: 13,
                  cursor: 'pointer',
                  textAlign: 'left',
                  transition: 'background var(--app-transition-fast)',
                }}
                onMouseEnter={(e) => {
                  if (!active) e.currentTarget.style.background = 'var(--app-sidebar-bg-hover)';
                }}
                onMouseLeave={(e) => {
                  if (!active) e.currentTarget.style.background = 'transparent';
                }}
              >
                <span style={{ flex: 1, minWidth: 0 }}>{child.label}</span>
                {child.badge ? <NavBadgePill badge={child.badge} /> : null}
              </button>
            </li>
          );
        })}
      </ul>
    </div>
  );
}
