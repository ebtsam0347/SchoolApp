-- Standard Forward Sports leave catalogue. Provisioning is done at
-- employee.created time — the leave types live here so the consumer
-- can resolve them by code without an admin pre-populating the table.
insert into leave_types (code, name, paid, default_per_year, accrual_rule,
                         carry_forward_max, requires_approval, active,
                         created_by, updated_by) values
    ('ANNUAL',    'Annual leave',    true,  14, 'ANNUAL',  7, true, true, 'system', 'system'),
    ('SICK',      'Sick leave',      true,  10, 'ANNUAL',  0, true, true, 'system', 'system'),
    ('CASUAL',    'Casual leave',    true,  10, 'ANNUAL',  0, true, true, 'system', 'system'),
    ('UNPAID',    'Unpaid leave',    false,  0, 'NONE',    0, true, true, 'system', 'system'),
    ('MATERNITY', 'Maternity leave', true,  90, 'NONE',    0, true, true, 'system', 'system'),
    ('PATERNITY', 'Paternity leave', true,  10, 'NONE',    0, true, true, 'system', 'system');
