-- Templates for chat bell-drops. Previously dispatched ad-hoc from
-- EventDispatcher.onChatMessageSent — moved to the standard templated
-- path so the link / wording / opt-out behave like every other type.
--
-- Placeholders filled at render time from the chat.message.sent payload:
--   {{senderName}}      — auth username (falls back to "Someone" /
--                         "a colleague" in EventDispatcher when blank).
--   {{preview}}         — first ~80 chars of the message body.
--   {{conversationId}}  — target conversation id (drives the FE deep link).

insert into notification_templates
    (type, title_tpl, body_tpl, link_tpl, category, label, description, created_by, updated_by) values
('CHAT_DM',
 'New message from {{senderName}}',
 '{{preview}}',
 '/chat/conversations/{{conversationId}}',
 'SYSTEM',
 'New direct message',
 'Someone sent you a direct message.',
 'system', 'system'),
('CHAT_MENTION',
 '{{senderName}} mentioned you',
 '{{preview}}',
 '/chat/conversations/{{conversationId}}',
 'SYSTEM',
 'Mentioned in chat',
 'You were @mentioned in a group or announcement.',
 'system', 'system');
