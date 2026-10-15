// Mock SSE shim. Real ai-service streams `delta` events over an
// SseEmitter for every Ollama token, then a final `done` event with
// the message id and citations. This generator mirrors that shape so
// the chat UI can be wired against the real endpoint later by swapping
// the source of the iterator — nothing else changes at the call site.

import type { Citation } from './data';

export interface DeltaEvent {
  type: 'delta';
  text: string;
}

export interface DoneEvent {
  type: 'done';
  messageId: string;
  citations?: Citation[];
  promptTokens?: number;
  completionTokens?: number;
}

export type StreamEvent = DeltaEvent | DoneEvent;

export interface StreamOptions {
  citations?: Citation[];
  signal?: AbortSignal;
  /** Per-token delay range in ms. Default ~20–60ms to feel like a real LLM. */
  minDelayMs?: number;
  maxDelayMs?: number;
}

const sleep = (ms: number) => new Promise<void>((resolve) => setTimeout(resolve, ms));

function tokenize(text: string): string[] {
  // Split on whitespace boundaries while keeping the trailing whitespace —
  // gives a chunky, word-at-a-time reveal that reads as "typing" rather than
  // a character-by-character crawl. Newlines become their own chunks so
  // markdown breaks render cleanly mid-stream.
  const tokens: string[] = [];
  const re = /\n|[^\s]+\s*/g;
  let m;
  while ((m = re.exec(text)) !== null) tokens.push(m[0]);
  return tokens;
}

let messageCounter = 1;
const newMessageId = () => `m-${Date.now().toString(36)}-${messageCounter++}`;

export async function* mockStream(
  reply: string,
  options: StreamOptions = {},
): AsyncGenerator<StreamEvent, void, unknown> {
  const { signal, citations, minDelayMs = 20, maxDelayMs = 60 } = options;
  const tokens = tokenize(reply);

  // Small head start so the typing indicator is visible for a beat.
  await sleep(180);

  let promptTokens = 0;
  let completionTokens = 0;

  for (const t of tokens) {
    if (signal?.aborted) return;
    const jitter = minDelayMs + Math.random() * (maxDelayMs - minDelayMs);
    await sleep(jitter);
    completionTokens += 1;
    yield { type: 'delta', text: t };
  }

  // Fake but plausible token counts for the done payload.
  promptTokens = 240 + Math.floor(Math.random() * 200);

  yield {
    type: 'done',
    messageId: newMessageId(),
    citations,
    promptTokens,
    completionTokens,
  };
}
