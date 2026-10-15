import { createFileRoute } from '@tanstack/react-router';
import { ChartOfAccounts } from '@/accounts';

export const Route = createFileRoute('/_app/accounts/coa')({
  component: ChartOfAccounts,
});
