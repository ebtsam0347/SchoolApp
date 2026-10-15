import { createFileRoute } from '@tanstack/react-router';
import { CashFlow } from '@/accounts';

export const Route = createFileRoute('/_app/accounts/reports/cash-flow')({
  component: CashFlow,
});
