import { createFileRoute } from '@tanstack/react-router';
import { TrialBalance } from '@/accounts';

export const Route = createFileRoute('/_app/accounts/trial-balance')({
  component: TrialBalance,
});
