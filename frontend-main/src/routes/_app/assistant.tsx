// /assistant — AI Assistant landing. Shows the "coming soon" screen until
// the assistant is live; reachable from the topbar launcher.

import { createFileRoute } from '@tanstack/react-router';
import { AssistantComingSoon } from '@/ai/coming-soon/AssistantComingSoon';

export const Route = createFileRoute('/_app/assistant')({
  component: AssistantComingSoon,
});
