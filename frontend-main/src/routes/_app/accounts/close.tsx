import { createFileRoute } from '@tanstack/react-router';
import { PeriodClose } from '@/accounts';

export const Route = createFileRoute('/_app/accounts/close')({
  component: PeriodClose,
});
