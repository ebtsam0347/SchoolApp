import { createFileRoute } from '@tanstack/react-router';
import { FixedAssets } from '@/accounts';

export const Route = createFileRoute('/_app/accounts/assets')({
  component: FixedAssets,
});
