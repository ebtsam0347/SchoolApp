// Topbar search affordance. In wide layouts it's a 360px-wide search-pill
// with placeholder text + ⌘K hint. In compact (mobile/narrow) layouts it
// collapses to a square IconButton — the palette is the same in both.

import { Search } from 'lucide-react';
import { KbdHint } from '@/components/KbdHint';
import { TopbarIconButton } from './IconButton';

export function SearchTrigger({
  onClick,
  compact,
}: {
  onClick: () => void;
  compact?: boolean;
}) {
  if (compact) {
    return (
      <TopbarIconButton label="Search (⌘K)" onClick={onClick} variant="outline">
        <Search size={16} strokeWidth={1.75} />
      </TopbarIconButton>
    );
  }
  return (
    <button
      type="button"
      onClick={onClick}
      style={{
        display: 'flex',
        alignItems: 'center',
        gap: 10,
        width: 360,
        maxWidth: '40vw',
        height: 36,
        padding: '0 12px',
        borderRadius: 'var(--app-radius-md)',
        border: '1px solid var(--app-topbar-border)',
        background: 'transparent',
        color: 'var(--app-topbar-text-muted)',
        cursor: 'pointer',
        fontSize: 13,
      }}
      aria-label="Open search"
    >
      <Search size={16} strokeWidth={1.75} />
      <span style={{ flex: 1, textAlign: 'left' }}>
        Search people, payslips, policies…
      </span>
      <KbdHint keys="⌘K" size="sm" />
    </button>
  );
}
