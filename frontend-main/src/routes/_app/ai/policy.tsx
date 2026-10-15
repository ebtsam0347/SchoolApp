import { createFileRoute } from '@tanstack/react-router';
import { PolicyAsk } from '@/ai';

export const Route = createFileRoute('/_app/ai/policy')({
  component: PolicyAsk,
});
