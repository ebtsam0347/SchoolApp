-- §7.notification — LEAVE_CANCELLED template. Emitted when a leave is
-- cancelled (employee self-cancel before start, or termination wipe).
-- Goes to the employee + the original approver chain so the inbox
-- doesn't show stale "awaiting your review" rows.

insert into notification_templates
    (type, title_tpl, body_tpl, link_tpl, category, created_by, updated_by)
values (
    'LEAVE_CANCELLED',
    'Leave cancelled',
    'Your leave for {{fromDate}} – {{toDate}} ({{days}} day(s)) was cancelled.',
    '/leaves/{{requestId}}',
    'APPROVAL',
    'system', 'system'
);

update notification_templates
   set label = 'Leave cancelled',
       description = 'A previously-pending or future-dated leave was cancelled (self-cancel or termination wipe).'
 where type = 'LEAVE_CANCELLED';
