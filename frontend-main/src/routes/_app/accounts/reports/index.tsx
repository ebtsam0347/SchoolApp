import { createFileRoute } from '@tanstack/react-router';
import { ReportsLanding } from '@/accounts';

export const Route = createFileRoute('/_app/accounts/reports/')({
  component: ReportsLanding,
});
