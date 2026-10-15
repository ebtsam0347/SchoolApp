-- Audit follow-up: the TRAVEL_REQUEST approval chain no longer has an HR
-- stage (employee-service V0618) — the chain is now TRANSPORT -> DD/
-- Requester's Head, with DD/Head final. travel.hr.approve stays a valid
-- permission (kept intact per business instruction — not removed, and not
-- currently reused by any other chain), but its stored description still
-- read "HR final approval of a travel request (stage 3)", which the admin
-- role console (GET /admin/permissions -> PermissionDtoMapper) surfaces
-- verbatim to whoever configures roles. Correct the description only; the
-- permission code, its role grants and every other row from V0033 are
-- untouched.

update permissions
   set name = 'Reserved: HR travel approval permission — not currently used by any active Travel approval stage',
       updated_by = 'system'
 where code = 'travel.hr.approve';
