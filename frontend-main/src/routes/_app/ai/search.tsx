import { createFileRoute } from '@tanstack/react-router';
import { SemanticSearch } from '@/ai';

export const Route = createFileRoute('/_app/ai/search')({
  component: SemanticSearch,
});
