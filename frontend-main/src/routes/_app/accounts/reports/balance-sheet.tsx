import { createFileRoute } from '@tanstack/react-router';
import { BalanceSheet } from '@/accounts';

export const Route = createFileRoute('/_app/accounts/reports/balance-sheet')({
  component: BalanceSheet,
});
