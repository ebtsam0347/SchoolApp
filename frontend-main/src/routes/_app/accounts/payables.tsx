import { createFileRoute } from '@tanstack/react-router';
import { VendorBills } from '@/accounts';

export const Route = createFileRoute('/_app/accounts/payables')({
  component: VendorBills,
});
