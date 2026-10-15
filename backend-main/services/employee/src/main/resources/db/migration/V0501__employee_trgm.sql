-- F4 from EMPLOYEE_BFF_WISHLIST.md — pg_trgm typo-tolerant search.
--
-- Two layers:
--   1. Speed: gin_trgm_ops indices accelerate the existing
--      `lower(...) like '%q%'` filters even with leading wildcards.
--   2. Typo tolerance: enables `similarity()`-based fuzzy match in the
--      search query. EmployeeRepository.search adds OR branches that hit
--      the same indices, so "ahmed" can find "ahmad" without a full scan.
--
-- pg_trgm is installed into the employee schema so the connection's
-- search_path can stay scoped to employee (no need to leak public onto
-- the JDBC connection).

create extension if not exists pg_trgm with schema employee;

create index if not exists idx_employees_fullname_trgm
    on employees using gin (lower(full_name) gin_trgm_ops);

create index if not exists idx_employees_code_trgm
    on employees using gin (lower(employee_code) gin_trgm_ops);

create index if not exists idx_employees_workemail_trgm
    on employees using gin (lower(coalesce(work_email, '')) gin_trgm_ops);
