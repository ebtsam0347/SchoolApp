import { createFileRoute } from '@tanstack/react-router';
import { CashPosition } from '@/accounts';

export const Route = createFileRoute('/_app/accounts/cash')({
  component: CashPosition,
});
