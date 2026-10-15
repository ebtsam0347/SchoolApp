-- Team request generation role and permissions.
-- This role can generate team-level operational requests across leave,
-- advances, regularization, overtime, requisitions, and evaluation.

insert into permissions (code, name, created_by, updated_by) values
    ('team.request.generation.leave',            'Create team leave requests',                   'system', 'system'),
    ('team.request.generation.shortLeave',       'Create team short-leave requests',             'system', 'system'),
    ('team.request.generation.shortTermAdvance', 'Create team short-term-advance requests',      'system', 'system'),
    ('team.request.generation.longTermAdvance',  'Create team long-term-advance requests',       'system', 'system'),
    ('team.request.generation.regularization',   'Create team attendance-regularization requests','system', 'system'),
    ('team.request.generation.overtime',         'Create team overtime requests',                'system', 'system'),
    ('team.request.generation.requisition',      'Create team requisition requests',             'system', 'system'),
    ('team.request.generation.evaluation',       'Create team evaluation requests',              'system', 'system');

insert into roles (code, name, description, system, created_by, updated_by) values
    ('TEAM_REQUEST_GENERATION',
     'Team Request Generation',
     'Can generate team-level requests for leave, advances, regularization, overtime, requisitions, and evaluation.',
     true, 'system', 'system');

insert into role_permissions (role_id, permission_id)
select r.id, p.id
from roles r
join permissions p on p.code in (
    'team.request.generation.leave',
    'team.request.generation.shortLeave',
    'team.request.generation.shortTermAdvance',
    'team.request.generation.longTermAdvance',
    'team.request.generation.regularization',
    'team.request.generation.overtime',
    'team.request.generation.requisition',
    'team.request.generation.evaluation'
)
where r.code = 'TEAM_REQUEST_GENERATION';
