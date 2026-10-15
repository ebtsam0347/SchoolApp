// Hamburger that opens the mobile sidebar Drawer. Only visible <lg.

import { Menu as MenuIcon } from 'lucide-react';
import { TopbarIconButton } from './IconButton';

export function MobileMenuButton({ onOpen }: { onOpen: () => void }) {
  return (
    <TopbarIconButton label="Open menu" onClick={onOpen}>
      <MenuIcon size={18} strokeWidth={1.75} />
    </TopbarIconButton>
  );
}
