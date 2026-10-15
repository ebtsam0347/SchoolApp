// Footer row pinned to the bottom of the sidebar. Today only "Help & docs"
// lives here; future rows (status indicator, build version) drop in below.

import { HelpCircle } from 'lucide-react';

export function SidebarFooter({ collapsed }: { collapsed: boolean }) {
  return (
    <div
      style={{
        borderTop: '1px solid var(--app-sidebar-border)',
        padding: '8px 12px',
      }}
    >
      <button
        type="button"
        style={{
          display: 'flex',
          alignItems: 'center',
          gap: 12,
          width: '100%',
          height: 32,
          padding: collapsed ? '0 14px' : '0 10px',
          borderRadius: 'var(--app-radius-sm)',
          border: 'none',
          background: 'transparent',
          color: 'var(--app-sidebar-text-muted)',
          cursor: 'pointer',
          textAlign: 'left',
        }}
        onMouseEnter={(e) => (e.currentTarget.style.background = 'var(--app-sidebar-bg-hover)')}
        onMouseLeave={(e) => (e.currentTarget.style.background = 'transparent')}
      >
        <HelpCircle size={16} strokeWidth={1.75} style={{ flexShrink: 0 }} />
        {!collapsed && <span style={{ fontSize: 13 }}>Help & docs</span>}
      </button>
    </div>
  );
}
