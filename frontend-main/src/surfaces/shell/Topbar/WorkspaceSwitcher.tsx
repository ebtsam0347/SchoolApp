// Workspace switcher. HR is the only workspace — the Accounts and Assistant
// previews were removed. Kept mounted so the Topbar still names where you
// are, and so restoring a workspace means appending to WORKSPACES rather than
// re-introducing the control.

import { Dropdown, theme as antdTheme, type MenuProps } from 'antd';
import { useNavigate } from '@tanstack/react-router';
import { Briefcase, ChevronDown } from 'lucide-react';

import { useWorkspaceStore, type Workspace } from '../workspace-store';

const { useToken } = antdTheme;

interface WorkspaceMeta {
  key: Workspace;
  label: string;
  description: string;
  icon: typeof Briefcase;
  home: string;
}

const WORKSPACES: WorkspaceMeta[] = [
  {
    key: 'hr',
    label: 'HR',
    description: 'People, time, payroll',
    icon: Briefcase,
    home: '/',
  },
];

export function WorkspaceSwitcher() {
  const { token } = useToken();
  const workspace = useWorkspaceStore((s) => s.workspace);
  const setWorkspace = useWorkspaceStore((s) => s.setWorkspace);
  const navigate = useNavigate();

  const active = WORKSPACES.find((w) => w.key === workspace) ?? WORKSPACES[0];
  const ActiveIcon = active.icon;

  const items: MenuProps['items'] = WORKSPACES.map((w) => {
    const Icon = w.icon;
    return {
      key: w.key,
      label: (
        <div style={{ display: 'flex', flexDirection: 'column', gap: 2, padding: '4px 0' }}>
          <span style={{ fontWeight: 600 }}>{w.label}</span>
          <span style={{ fontSize: 11, color: token.colorTextSecondary }}>
            {w.description}
          </span>
        </div>
      ),
      icon: <Icon size={14} strokeWidth={1.75} />,
    };
  });

  const onSelect: MenuProps['onClick'] = ({ key }) => {
    const target = WORKSPACES.find((w) => w.key === key);
    if (!target) return;
    setWorkspace(target.key);
    navigate({ to: target.home });
  };

  return (
    <Dropdown
      menu={{
        items,
        onClick: onSelect,
        selectedKeys: [active.key],
      }}
      trigger={['click']}
      placement="bottomLeft"
    >
      <button
        type="button"
        aria-label={`Workspace: ${active.label}`}
        style={{
          display: 'inline-flex',
          alignItems: 'center',
          gap: 8,
          height: 32,
          padding: '0 10px',
          background: 'transparent',
          border: '1px solid var(--app-topbar-border)',
          borderRadius: 'var(--app-radius-sm)',
          color: 'var(--app-topbar-text)',
          cursor: 'pointer',
          fontSize: 13,
          fontWeight: 600,
          flexShrink: 0,
        }}
      >
        <ActiveIcon size={14} strokeWidth={1.75} />
        <span>{active.label}</span>
        <ChevronDown size={12} strokeWidth={2} style={{ opacity: 0.6 }} />
      </button>
    </Dropdown>
  );
}
