import { createFileRoute } from '@tanstack/react-router';
import { JournalEntries } from '@/accounts';

export const Route = createFileRoute('/_app/accounts/journal')({
  component: JournalEntries,
});
