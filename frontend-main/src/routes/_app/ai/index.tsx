import { createFileRoute, redirect } from '@tanstack/react-router';

// /ai — redirect to the headline page. Chat is the canonical landing.

export const Route = createFileRoute('/_app/ai/')({
  beforeLoad: () => {
    throw redirect({ to: '/ai/chat' });
  },
});
