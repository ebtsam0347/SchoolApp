-- Classify each device by its physical role. Most are ATTENDANCE; some
-- units at the canteen entrance or perimeter gates emit ZK punches but
-- are not attendance signals. The flag is metadata-only today — HR sets
-- it from the admin UI. Downstream filtering (e.g. excluding canteen
-- punches from the attendance recompute) comes when it's actually needed.

alter table attendance_devices
    add column device_type varchar(16) not null default 'ATTENDANCE';
