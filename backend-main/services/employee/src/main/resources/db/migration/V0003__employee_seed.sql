-- Seed catalogs HR will start from. Everything is `active=true`; HR can
-- soft-deactivate later. Adding more rows is a routine HR task — not a
-- migration.

-- Pakistan provinces + ICT/AJK/GB. Codes match ISO 3166-2:PK where
-- possible.
insert into provinces (code, name, created_by, updated_by) values
    ('PB',  'Punjab',                          'system', 'system'),
    ('SD',  'Sindh',                           'system', 'system'),
    ('KP',  'Khyber Pakhtunkhwa',              'system', 'system'),
    ('BL',  'Balochistan',                     'system', 'system'),
    ('ICT', 'Islamabad Capital Territory',     'system', 'system'),
    ('AJK', 'Azad Jammu and Kashmir',          'system', 'system'),
    ('GB',  'Gilgit-Baltistan',                'system', 'system');

-- A starter set of cities. Sialkot first — Forward Sports' workforce is
-- overwhelmingly Sialkot-based (per docs). HR seeds more on demand.
insert into cities (province_id, code, name, created_by, updated_by)
select p.id, c.code, c.name, 'system', 'system'
from provinces p
join (values
    ('PB',  'SKT',  'Sialkot'),
    ('PB',  'LHE',  'Lahore'),
    ('PB',  'GUJ',  'Gujranwala'),
    ('PB',  'FSD',  'Faisalabad'),
    ('PB',  'RWP',  'Rawalpindi'),
    ('PB',  'MUL',  'Multan'),
    ('SD',  'KHI',  'Karachi'),
    ('SD',  'HYD',  'Hyderabad'),
    ('KP',  'PEW',  'Peshawar'),
    ('BL',  'QTA',  'Quetta'),
    ('ICT', 'ISB',  'Islamabad'),
    ('AJK', 'MZD',  'Muzaffarabad'),
    ('GB',  'GIL',  'Gilgit')
) as c(province_code, code, name)
on c.province_code = p.code;

-- Common qualifications. Codes follow degree shorthand. Levels match
-- the catalog's `level` enum (MATRIC | INTERMEDIATE | DIPLOMA | BACHELORS
-- | MASTERS | MPHIL | PHD | OTHER).
insert into qualifications (code, name, level, created_by, updated_by) values
    ('MATRIC',         'Matric (SSC)',                       'MATRIC',       'system', 'system'),
    ('FSC_PRE_ENG',    'FSc Pre-Engineering',                'INTERMEDIATE', 'system', 'system'),
    ('FSC_PRE_MED',    'FSc Pre-Medical',                    'INTERMEDIATE', 'system', 'system'),
    ('ICOM',           'ICom (Intermediate of Commerce)',    'INTERMEDIATE', 'system', 'system'),
    ('ICS',            'ICS (Intermediate of Computer Sci.)', 'INTERMEDIATE', 'system', 'system'),
    ('FA',             'FA (Faculty of Arts)',               'INTERMEDIATE', 'system', 'system'),
    ('DAE',            'Diploma of Associate Engineering',   'DIPLOMA',      'system', 'system'),
    ('BSC_CS',         'BSc Computer Science',               'BACHELORS',    'system', 'system'),
    ('BCS',            'BCS (Bachelor of Computer Science)', 'BACHELORS',    'system', 'system'),
    ('BBA',            'BBA',                                'BACHELORS',    'system', 'system'),
    ('BCOM',           'BCom',                               'BACHELORS',    'system', 'system'),
    ('BA',             'BA',                                 'BACHELORS',    'system', 'system'),
    ('BE',             'BE / B.Eng',                         'BACHELORS',    'system', 'system'),
    ('LLB',            'LLB',                                'BACHELORS',    'system', 'system'),
    ('MBBS',           'MBBS',                               'BACHELORS',    'system', 'system'),
    ('MSC_CS',         'MSc Computer Science',               'MASTERS',      'system', 'system'),
    ('MBA',            'MBA',                                'MASTERS',      'system', 'system'),
    ('MCOM',           'MCom',                               'MASTERS',      'system', 'system'),
    ('MA',             'MA',                                 'MASTERS',      'system', 'system'),
    ('MS',             'MS',                                 'MPHIL',        'system', 'system'),
    ('MPHIL',          'MPhil',                              'MPHIL',        'system', 'system'),
    ('PHD',            'PhD',                                'PHD',          'system', 'system');

-- A starter skill catalog. Forward Sports manufactures sports goods
-- (footballs, etc.) so the catalog leans toward TRADE skills alongside
-- the usual office/tech ones. HR adds more as they appear.
insert into skills (code, name, category, created_by, updated_by) values
    ('LANG_URDU',       'Urdu',                          'LANGUAGE', 'system', 'system'),
    ('LANG_ENGLISH',    'English',                       'LANGUAGE', 'system', 'system'),
    ('LANG_PUNJABI',    'Punjabi',                       'LANGUAGE', 'system', 'system'),
    ('TECH_JAVA',       'Java',                          'TECH',     'system', 'system'),
    ('TECH_PYTHON',     'Python',                        'TECH',     'system', 'system'),
    ('TECH_SQL',        'SQL',                           'TECH',     'system', 'system'),
    ('TECH_EXCEL',      'Microsoft Excel',               'TECH',     'system', 'system'),
    ('TECH_ERP',        'ERP / SAP',                     'TECH',     'system', 'system'),
    ('TRADE_STITCHING', 'Hand stitching (footballs)',    'TRADE',    'system', 'system'),
    ('TRADE_QC',        'Quality control inspection',    'TRADE',    'system', 'system'),
    ('TRADE_LEATHER',   'Leather cutting',               'TRADE',    'system', 'system'),
    ('TRADE_FORKLIFT',  'Forklift operation',            'TRADE',    'system', 'system'),
    ('SPORT_FOOTBALL',  'Football (sport)',              'SPORT',    'system', 'system');
