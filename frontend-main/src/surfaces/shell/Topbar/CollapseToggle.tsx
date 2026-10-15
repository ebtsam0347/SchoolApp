// Desktop sidebar collapse/expand button. Icon flips based on current state.

import { PanelLeftClose, PanelLeftOpen } from 'lucide-react';
import { TopbarIconButton } from './IconButton';

export function CollapseToggle({
  collapsed,
  onToggle,
}: {
  collapsed: boolean;
  onToggle: () => void;
}) {
  const Icon = collapsed ? PanelLeftOpen : PanelLeftClose;
  const label = collapsed ? 'Expand sidebar' : 'Collapse sidebar';
  return (
    <TopbarIconButton label={label} onClick={onToggle}>
      <Icon size={18} strokeWidth={1.75} />
    </TopbarIconButton>
  );
}
