export {
  conversationsApi,
  messagesApi,
  attachmentsApi,
  searchApi,
  presenceApi,
  broadcastApi,
  adminChatApi,
  type ListMessagesParams,
  type SearchParams,
} from './api';

export { chatBff } from './bff-api';

export type {
  ConversationDto,
  ConversationType,
  MemberRole,
  DeleteReason,
  PresenceStatus,
  AttachmentDto,
  AttachmentRef,
  MessageDto,
  MessagePageResponse,
  PresenceDto,
  CreateDmRequest,
  CreateGroupRequest,
  AddMemberRequest,
  SendMessageRequest,
  EditMessageRequest,
  MarkReadRequest,
  TypingRequest,
  ChatUploadUrlRequest,
  ChatUploadUrlResponse,
  SearchConversationHeader,
  SearchResultsResponse,
  UserIdentitySummary,
  UsersByIdsResponse,
  ChatInboxResponse,
  ChatMessageEnvelope,
  ChatMessageEditedEnvelope,
  ChatMessageDeletedEnvelope,
  ChatMessageDeliveredEnvelope,
  ChatReadEnvelope,
  ChatTypingEnvelope,
  PresenceChangedEnvelope,
} from './types';

export {
  CONVERSATION_TYPE_LABEL,
  MEMBER_ROLE_LABEL,
  MEMBER_ROLE_TONE,
  DELETE_REASON_LABEL,
  NO_MESSAGES_PLACEHOLDER,
  bodyPreview,
  conversationDisplayTitle,
  initialsOf,
} from './format';

export { UserAvatar, type UserAvatarProps } from './UserAvatar';

export {
  canBroadcast,
  canAdminRead,
  canAdminDelete,
} from './permissions';

export { uploadAttachment, type UploadProgress } from './attachments';

export {
  useChatPresence,
  useTypingFor,
  usePresenceFor,
} from './presence-store';

export { chatStreamHandlers } from './stream-handlers';
export { chatKeys } from './query-keys';
export { readDraft, writeDraft, clearDraft } from './drafts';
