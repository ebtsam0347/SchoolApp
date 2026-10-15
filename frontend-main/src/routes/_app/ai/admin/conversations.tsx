import { createFileRoute } from '@tanstack/react-router';
import { AdminConversations } from '@/ai';

export const Route = createFileRoute('/_app/ai/admin/conversations')({
  component: AdminConversations,
});
