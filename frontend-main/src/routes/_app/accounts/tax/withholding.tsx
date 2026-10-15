import { createFileRoute } from '@tanstack/react-router';
import { Withholding } from '@/accounts';

export const Route = createFileRoute('/_app/accounts/tax/withholding')({
  component: Withholding,
});
