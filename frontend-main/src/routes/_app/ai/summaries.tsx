import { createFileRoute } from '@tanstack/react-router';
import { SummariesGallery } from '@/ai';

export const Route = createFileRoute('/_app/ai/summaries')({
  component: SummariesGallery,
});
