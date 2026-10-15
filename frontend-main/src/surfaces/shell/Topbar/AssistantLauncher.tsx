// Topbar entry point for the AI Assistant. The assistant itself isn't live
// yet — the button routes to /assistant, which renders the "coming soon"
// screen. In compact layouts it collapses to the shared square IconButton.

import { useNavigate } from '@tanstack/react-router';
import { Sparkles } from 'lucide-react';
import { TopbarIconButton } from './IconButton';

export function AssistantLauncher({ compact }: { compact?: boolean }) {
  const navigate = useNavigate();
  const open = () => void navigate({ to: '/assistant' });

  if (compact) {
    return (
      <TopbarIconButton label="AI Assistant" onClick={open} variant="outline">
        <Sparkles size={16} strokeWidth={1.75} />
      </TopbarIconButton>
    );
  }
  return (
    <button
      type="button"
      onClick={open}
      aria-label="Open AI Assistant"
      style={{
        display: 'inline-flex',
        alignItems: 'center',
        gap: 8,
        height: 36,
        padding: '0 14px',
        borderRadius: 'var(--app-radius-md)',
        border: 'none',
        background:
          'linear-gradient(135deg, var(--app-accent-grad-from), var(--app-accent-grad-to))',
        boxShadow: '0 6px 18px -8px var(--app-accent-glow)',
        color: 'var(--app-on-accent-fg)',
        cursor: 'pointer',
        fontSize: 13,
        fontWeight: 600,
        whiteSpace: 'nowrap',
        flexShrink: 0,
      }}
    >
      <Sparkles size={15} strokeWidth={2} />
      AI Assistant
    </button>
  );
}
