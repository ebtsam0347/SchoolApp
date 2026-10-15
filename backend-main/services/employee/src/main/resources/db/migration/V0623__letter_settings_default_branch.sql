-- Branch printed in the appointment letter's Official Information panel when
-- the request doesn't override it. Single-site company today, so one
-- company-wide default is enough; nullable so a blank prints as blank.
alter table letter_settings add column default_branch varchar(128);

update letter_settings set default_branch = 'Main Branch (Sahowala)';
