import { createFileRoute } from '@tanstack/react-router';
import { SalesTax } from '@/accounts';

export const Route = createFileRoute('/_app/accounts/tax/sales')({
  component: SalesTax,
});
