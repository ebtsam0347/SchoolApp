-- Adds human-readable label + description to notification_templates.
-- These power the GET /notifications/types catalog so the FE preferences
-- and admin templates pages stop hardcoding the type list.
--
-- Backfills the seeded rows from V0002. Future templates ship with
-- label/description set in their own seed migration.

alter table notification_templates
    add column label       varchar(128),
    add column description text;

update notification_templates set label = 'Leave applied (approver)',
    description = 'A team member submitted a leave request and you are the next approver.'
    where type = 'LEAVE_APPLIED';
update notification_templates set label = 'Leave moved to next stage',
    description = 'A previous approver signed off on a leave; it is now your turn to review.'
    where type = 'LEAVE_STAGE_APPROVED';
update notification_templates set label = 'Leave rejected at a stage',
    description = 'Your leave request was rejected mid-flow by an approver.'
    where type = 'LEAVE_STAGE_REJECTED';
update notification_templates set label = 'Leave fully approved',
    description = 'All approval stages cleared — your leave is approved.'
    where type = 'LEAVE_APPROVED';
update notification_templates set label = 'Leave partially approved',
    description = 'Some days of your leave were approved, others were not.'
    where type = 'LEAVE_PARTIALLY_APPROVED';
update notification_templates set label = 'Leave rejected',
    description = 'Your leave request was rejected.'
    where type = 'LEAVE_REJECTED';

update notification_templates set label = 'Overtime requested (approver)',
    description = 'A team member requested overtime and you are the next approver.'
    where type = 'OT_REQUESTED';
update notification_templates set label = 'Overtime moved to next stage',
    description = 'A previous approver signed off on overtime; it is now your turn to review.'
    where type = 'OT_STAGE_APPROVED';
update notification_templates set label = 'Overtime rejected at a stage',
    description = 'Your overtime was rejected mid-flow by an approver.'
    where type = 'OT_STAGE_REJECTED';
update notification_templates set label = 'Overtime approved',
    description = 'All approval stages cleared — your overtime is approved.'
    where type = 'OT_APPROVED';
update notification_templates set label = 'Overtime rejected',
    description = 'Your overtime was rejected.'
    where type = 'OT_REJECTED';

update notification_templates set label = 'Regularization requested (approver)',
    description = 'A team member requested attendance regularization and you are the next approver.'
    where type = 'REGULARIZATION_REQUESTED';
update notification_templates set label = 'Regularization moved to next stage',
    description = 'A previous approver signed off; it is now your turn to review.'
    where type = 'REGULARIZATION_STAGE_APPROVED';
update notification_templates set label = 'Regularization rejected at a stage',
    description = 'Your regularization request was rejected mid-flow by an approver.'
    where type = 'REGULARIZATION_STAGE_REJECTED';
update notification_templates set label = 'Regularization approved',
    description = 'Your attendance regularization request is approved.'
    where type = 'REGULARIZATION_APPROVED';
update notification_templates set label = 'Regularization rejected',
    description = 'Your attendance regularization request was rejected.'
    where type = 'REGULARIZATION_REJECTED';

update notification_templates set label = 'Payslip ready',
    description = 'Your monthly payslip has been generated and is available to view.'
    where type = 'PAYSLIP_READY';

update notification_templates set label = 'Password reset by admin',
    description = 'An administrator reset your password and signed all your devices out.'
    where type = 'PASSWORD_RESET_BY_ADMIN';
update notification_templates set label = 'Signed out by admin',
    description = 'An administrator signed you out of one of your devices.'
    where type = 'SESSION_REVOKED_BY_ADMIN';

update notification_templates set label = 'Manager changed (you)',
    description = 'Your reporting manager was changed.'
    where type = 'EMPLOYEE_MANAGER_CHANGED_EMPLOYEE';
update notification_templates set label = 'Direct report moved away',
    description = 'A direct report no longer reports to you.'
    where type = 'EMPLOYEE_MANAGER_CHANGED_OLD_MANAGER';
update notification_templates set label = 'New direct report',
    description = 'A new team member now reports to you.'
    where type = 'EMPLOYEE_MANAGER_CHANGED_NEW_MANAGER';
update notification_templates set label = 'Team member suspended',
    description = 'A team member was suspended.'
    where type = 'EMPLOYEE_SUSPENDED';
update notification_templates set label = 'Suspension lifted (you)',
    description = 'Your suspension was lifted and your access is restored.'
    where type = 'EMPLOYEE_SUSPENSION_LIFTED';
update notification_templates set label = 'Suspension lifted (team member)',
    description = 'A suspension on a team member was lifted.'
    where type = 'EMPLOYEE_SUSPENSION_LIFTED_MANAGER';

update notification_templates set label = 'Shift override applied',
    description = 'Your expected hours changed for one or more days.'
    where type = 'SHIFT_OVERRIDDEN';
update notification_templates set label = 'Shift override removed',
    description = 'A shift override on your schedule was removed.'
    where type = 'SHIFT_OVERRIDE_REVERTED';

update notification_templates set label = 'Announcement',
    description = 'Company-wide or targeted broadcast announcement.'
    where type = 'ANNOUNCEMENT';
