// /ai/chat — assistant chat workspace.
//
// `?c=<conversationId>` selects an active conversation. The id lives in
// the URL so refresh / share-a-link works and the back button moves
// between conversations naturally.

import { createFileRoute, useNavigate } from '@tanstack/react-router';

import { ChatWorkspace } from '@/ai';

type ChatSearch = { c?: string };

export const Route = createFileRoute('/_app/ai/chat')({
  component: ChatRoute,
  validateSearch: (search: Record<string, unknown>): ChatSearch => ({
    c: typeof search.c === 'string' ? search.c : undefined,
  }),
});

function ChatRoute() {
  const { c } = Route.useSearch();
  const navigate = useNavigate({ from: Route.fullPath });

  const setActiveId = (id: string | null) => {
    navigate({
      search: (prev) => ({ ...prev, c: id ?? undefined }),
      replace: true,
    });
  };

  return <ChatWorkspace activeId={c ?? null} setActiveId={setActiveId} />;
}
