// Assistant (Preview) module barrel — mock-only surface for the demo.
// All data flows from src/ai/mock; no api.ts / bff-api.ts yet.

export * from './mock/data';
export { mockStore } from './mock/store';
export { mockStream, type StreamEvent, type DeltaEvent, type DoneEvent } from './mock/stream';
export { ChatWorkspace } from './chat/ChatWorkspace';
export { PolicyAsk } from './policy/PolicyAsk';
export { SemanticSearch } from './search/SemanticSearch';
export { SummariesGallery } from './summarize/SummariesGallery';
export { AdminConversations } from './admin/AdminConversations';
export { IngestRuns } from './admin/IngestRuns';
