import { createFileRoute } from '@tanstack/react-router';
import { BankBook } from '@/accounts';

export const Route = createFileRoute('/_app/accounts/bank')({
  component: BankBook,
});
