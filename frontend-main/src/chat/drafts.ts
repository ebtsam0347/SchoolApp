// Per-conversation composer drafts persisted in localStorage. Tiny —
// one key, one shape, no Zustand. Modelled on `auth-storage.ts` and
// `palette-recents.ts`.

const KEY = 'fshrms.chat.drafts.v1';

type DraftMap = Record<string, string>;

function read(): DraftMap {
  try {
    const raw = localStorage.getItem(KEY);
    if (!raw) return {};
    const parsed = JSON.parse(raw) as unknown;
    return parsed && typeof parsed === 'object' ? (parsed as DraftMap) : {};
  } catch {
    return {};
  }
}

function write(map: DraftMap) {
  try {
    localStorage.setItem(KEY, JSON.stringify(map));
  } catch {
    // localStorage full or disabled — drafts aren't worth surfacing an error.
  }
}

export function readDraft(conversationId: number): string {
  return read()[String(conversationId)] ?? '';
}

export function writeDraft(conversationId: number, body: string) {
  const map = read();
  if (body) map[String(conversationId)] = body;
  else delete map[String(conversationId)];
  write(map);
}

export function clearDraft(conversationId: number) {
  writeDraft(conversationId, '');
}
