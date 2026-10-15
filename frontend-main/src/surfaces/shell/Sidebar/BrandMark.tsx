// Brand mark at the top of the sidebar.
//
// Renders the school crest + name. In rail (collapsed) mode
// only the crest shows and a Tooltip carries the full name.

import { Tooltip } from 'antd';

export function BrandMark({ collapsed }: { collapsed: boolean }) {
  const content = (
    <div
      style={{
        height: 64,
        width: '100%',
        display: 'flex',
        alignItems: 'center',
        gap: 12,
        padding: collapsed ? '0 18px' : '0 16px',
        borderBottom: '1px solid var(--app-sidebar-border)',
        color: 'inherit',
      }}
    >
      <img
        src="/icons/icon-192.png"
        alt=""
        aria-hidden="true"
        style={{
          width: 36,
          height: 36,
          borderRadius: 'var(--app-radius-sm)',
          flexShrink: 0,
          objectFit: 'cover',
        }}
      />
      {!collapsed && (
        <span style={{ display: 'flex', flexDirection: 'column', minWidth: 0, flex: 1 }}>
          <span
            style={{
              fontSize: 13,
              fontWeight: 600,
              color: 'var(--app-sidebar-text-active)',
              lineHeight: 1.2,
            }}
          >
            The Spirit of Islam School
          </span>
          <span style={{ fontSize: 11, color: 'var(--app-sidebar-text-muted)', lineHeight: 1.3 }}>
            Staff Portal
          </span>
        </span>
      )}
    </div>
  );

  if (collapsed) {
    return (
      <Tooltip title="The Spirit of Islam School · Staff Portal" placement="right">
        {content}
      </Tooltip>
    );
  }
  return content;
}
