import { createFileRoute } from '@tanstack/react-router';
import { Inventory } from '@/accounts';

export const Route = createFileRoute('/_app/accounts/inventory')({
  component: Inventory,
});
