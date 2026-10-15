import { createFileRoute } from '@tanstack/react-router';
import { Vendors } from '@/accounts';

export const Route = createFileRoute('/_app/accounts/vendors')({
  component: Vendors,
});
