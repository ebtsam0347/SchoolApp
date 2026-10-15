// /chat — index slot. Shown when no conversation is selected.

import { createFileRoute } from '@tanstack/react-router';
import { MessageSquare } from 'lucide-react';
import { EmptyState } from '@/components';

export const Route = createFileRoute('/_app/chat/')({
  component: ChatIndex,
});

function ChatIndex() {
  return (
    <div
      style={{
        display: 'flex',
        alignItems: 'center',
        justifyContent: 'center',
        height: '100%',
        padding: 32,
      }}
    >
      <EmptyState
        icon={MessageSquare}
        title="Pick a conversation"
        description="Or start a new direct message or group from the list on the left."
      />
    </div>
  );
}
