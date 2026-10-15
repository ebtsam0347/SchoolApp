import { createFileRoute } from '@tanstack/react-router';
import { IngestRuns } from '@/ai';

export const Route = createFileRoute('/_app/ai/admin/ingest')({
  component: IngestRuns,
});
