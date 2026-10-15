import { createFileRoute } from '@tanstack/react-router';
import { Cogs } from '@/accounts';

export const Route = createFileRoute('/_app/accounts/cogs')({
  component: Cogs,
});
