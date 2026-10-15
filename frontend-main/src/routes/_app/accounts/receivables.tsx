import { createFileRoute } from '@tanstack/react-router';
import { CustomerInvoices } from '@/accounts';

export const Route = createFileRoute('/_app/accounts/receivables')({
  component: CustomerInvoices,
});
