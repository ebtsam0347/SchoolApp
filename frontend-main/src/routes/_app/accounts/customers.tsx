import { createFileRoute } from '@tanstack/react-router';
import { Customers } from '@/accounts';

export const Route = createFileRoute('/_app/accounts/customers')({
  component: Customers,
});
