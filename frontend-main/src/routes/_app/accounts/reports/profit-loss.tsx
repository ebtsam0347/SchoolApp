import { createFileRoute } from '@tanstack/react-router';
import { ProfitLoss } from '@/accounts';

export const Route = createFileRoute('/_app/accounts/reports/profit-loss')({
  component: ProfitLoss,
});
