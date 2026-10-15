import { createFileRoute } from '@tanstack/react-router';
import { ExecutiveDashboard } from '@/accounts';

export const Route = createFileRoute('/_app/accounts/')({
  component: ExecutiveDashboard,
});
