-- Seed the single org-wide BROADCAST conversation. The
-- uk_conversations_one_broadcast partial unique index guarantees there
-- can only ever be one of these. Members are *not* pre-populated —
-- broadcast messages fan out by reading the full active-employee list
-- at send time (see ConversationService.broadcastRecipients()).
insert into conversations (type, title, created_by_user, created_by, updated_by) values
    ('BROADCAST', 'Announcements', 0, 'system', 'system');
