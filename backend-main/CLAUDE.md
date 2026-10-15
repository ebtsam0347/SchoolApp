# CLAUDE.md — FSHRMS

## Context

HRMS backend for **Forward Sports** (Sialkot, Punjab, Pakistan). Single-tenant.
Eight Java services + `commons` deployed to a light k3s on a single VM (UAT
live; Prod by tag promotion). Not high QPS, not many users — clarity beats
cleverness.

## How to help me plan

Load-bearing rules.

1. **Recommend, don't enumerate.** Pick the answer in one or two sentences.
   No A/B/C tradeoff tables. If it genuinely needs info I haven't given,
   say so in one line and ask the single question that unblocks it.
2. **Close open questions aggressively.** Only leave a question "open" if
   it needs info I don't have (regulator data, business policy, headcount,
   vendor specifics). A doc full of "TBD" is a planning smell.
3. **Plan realistically.** Small team. Light k3s. No platform team. Don't
   propose Temporal, service meshes, schema registries, multi-region,
   OpenSearch, Vault, etc. The right answer is usually a Postgres column
   and a cron job.
4. **Default to the simpler thing.** Postgres over Mongo. Hand-rolled state
   machine over framework. JSON over Avro. Synchronous RPC over eventual
   consistency unless there's a real reason.
5. **No new ideas without a reason in the request.** Don't add features,
   tables, services, or libraries I didn't ask for. If something would
   clearly help, mention it in one line — don't build it.
6. **Don't relitigate locked decisions.** If something is settled below or
   in a service doc, treat it as fact. Sync the docs if they conflict;
   don't reopen the call.
7. **Cut padding.** No "let me know if you want me to..." closers. No
   restating what you just did. No emojis.

## Locked-in technical decisions

Settled. Don't reopen.

- **Stack:** Spring Boot 4, Java 21, PostgreSQL 17, Redis 8, Kafka KRaft,
  MinIO. SB4 starter renames: `-web` → `-webmvc`, `-aop` → `-aspectj`; no
  umbrella `-test` (split per-feature: `-webmvc-test`, `-data-jpa-test`).
- **Group ID:** `com.forward.hrms`. Module artifactIds are bare names
  (`auth`, `commons`, …) — no `fshrms` prefix. All Java code + Maven wrapper
  live under `services/`; parent POM `services/pom.xml` (artifactId
  `services`, packaging `pom`). Repo root holds only docs and ops files.
  Build with `cd services && ./mvnw ...`.
- Maven multi-module **monorepo**. Eight services + `commons`.
- **Java service package layout** (worked example: `services/auth/`):
  ```
  com.forward.hrms.<svc>/
    <Svc>Application.java       — @SpringBootApplication entry point
    client/                     — outbound HTTP clients (RestClient wrappers)
    config/                     — @Configuration, @ConfigurationProperties, security chain
    controller/                 — @RestController only (no DTOs/services/helpers)
    dto/request/, dto/response/ — request/response records (@Schema-annotated)
    entity/                     — JPA @Entity classes
    enums/                      — domain + error-code enums
    events/                     — outbox payload records and @KafkaListener consumers
    repository/                 — Spring Data repositories
    service/                    — @Service business logic + small in-service helpers
    <feature>/                  — service-specific clusters (e.g. `jwt/`, `bootstrap/`).
                                  Use sparingly — when in doubt, the bucket wins.
  ```
  - Don't make per-feature sub-packages by default (`admin/`, `login/`).
    Bucketed layout is the convention; reach for a feature folder only when
    a tight cluster would otherwise scatter across 4+ buckets (the `jwt`
    cluster is the canonical example).
  - One class per file. No empty packages.
  - DTOs are records. Mappers (`SessionDto.of(Session)`) live as static
    methods on the DTO record itself, not a separate `mapper/` package.
- **Single Postgres DB, schema per service** (`auth`, `employee`,
  `attendance`, `payroll`, `notification`, `audit`). No cross-schema reads —
  services talk via APIs/events.
- **No `fshrms` / `fshrms_` prefix anywhere** — artifacts, modules, schemas,
  buckets, packages.
- **`bigint` identity primary keys.** Not UUIDs.
- **Uniform audit columns** on every business entity (skip bridges/system
  tables): `created_at`, `updated_at`, `created_by`, `updated_by`,
  `ip_address`. The `_by` columns store usernames.
- **FK columns are plain `Long`, not `@ManyToOne`.** Every foreign key on a
  business entity (`department_id`, `manager_id`, `employee_id` on child
  tables) is a plain `Long`. Service layer looks the catalog row up
  explicitly when it needs to validate or denormalize. Hot reads don't need
  the join, and explicit lookups beat `LAZY`/`EAGER` debugging. Narrow
  exceptions: `@ManyToMany` on `User.roles` (auth) and the
  `Section.department` / `City.province` parents on catalog rows where the
  parent code is wanted on every read.
- **Money:** `numeric(15,2)` (rupees, two decimals). Java side `BigDecimal`.
  **No unit suffix on field/column names** — type tells you the unit; use
  plain `basic`, `amount`, `gross`, `net`. Never floats, never `bigint`
  paisa. **Rounding:** any sub-paisa intermediate (LOP proration, OT, PESSI)
  rounds **up** — `RoundingMode.CEILING` at scale 2. Centralized in
  `commons` (`Money` helper).
- **Time zone:** server, JVM, DB run `TZ=Asia/Karachi`. Columns are plain
  `timestamp` (no `timestamptz`). `LocalDateTime.now()` /
  `LocalDate.now()` (no-arg) are correct. Don't reach for `Pakistan.ZONE` —
  single-tenant single-country.
- **Auth:** custom JWT (RS256), bcrypt cost 12, opaque refresh tokens in
  Redis + durable `sessions` rows. **No MFA. No public forgot-password.**
  ESS is opt-in per employee.
- **HR has `auth.users.write`** (`V0005__employee_perms.sql`) so the
  employee-first onboarding saga can call `auth POST /admin/users` during
  ESS provisioning. Don't roll back without rerouting onboarding through a
  service-account JWT.
- **Onboarding is employee-first.** Employee record before user record.
- **Termination disables immediately.** Resignation keeps access through the
  notice period; daily job emits `employee.terminated` on last working day.
- **Outbox pattern** in every writing service (hand-rolled poller).
- **Per-service audit topics** on Kafka: `<svc>.audit`.
- **`employee.updated` payload shape:**
  `{ employeeId, userId, changes: { fieldName: newValue, … } }`. Consumers
  read `changes.<field>` (auth syncs `users.full_name` from
  `changes.fullName`). Same shape for any future field-level update event.
- **Saga:** hand-rolled state machine in `commons-saga`. Forward-only with
  bounded retry per step — no compensation registry. Steps return
  `Done | Retry(reason) | Failed(code,msg)`; runner does exponential
  backoff and flips to `FAILED` after `hrms.saga.max-attempts-per-step`
  (default 8). Services using sagas add `commons-saga` and Flyway location
  `classpath:db/commons-saga` (V0500); table lives in the service's own
  schema. Commons modules use V05xx so they stay out of services' V000x.
  Live sagas (employee-service):
    - `ONBOARDING` → kickoff persists employee + contract + history +
      outbox `employee.created` + audit; `PROVISION_ESS` step calls auth
      `POST /admin/users` with `Idempotency-Key`, patches
      `employees.user_id`, emits audit.
    - `TERMINATION_DISABLE_AUTH` → kickoff flips status, lifts suspensions,
      emits `employee.terminated`; `DISABLE_AUTH_USER` step calls auth
      `POST /admin/users/{id}/disable` (idempotent). Auth's Kafka consumer
      for `employee.terminated` is the second path; both converge on the
      same idempotent disable.
  Visibility: `GET /sagas/{id}`, `GET /sagas?status=…&name=…` (paginated
  list; both filters optional), `GET /sagas/by-key?name=…&correlationKey=…`
  (exact pair lookup), `POST /sagas/{id}/retry` (employee-service; payroll
  exposes the same surface for its own sagas; gateway routes `/sagas/**`
  to employee and `/payroll/sagas/**` to payroll). `find()` lives on a
  distinct path so Springdoc doesn't collapse it with `list()` into a
  single operation whose parameter set marks `correlationKey` as required
  — that collapsed spec broke the operator console's typed client.
  Identity for runner-thread auth calls travels via the saga payload
  (`identity` snapshot of the kickoff caller's `X-User-*` headers).
- **API responses:** DTO (or `PageResponse<T>`) directly on success;
  RFC 7807 ProblemDetail with `code`, `correlationId`, `timestamp`, optional
  `violations[]` on errors. No success-wrapping envelope.
- **`caller.requireUser()` / `caller.requireEmployee()` for "no current
  user" guards.** Inject `CallerContext`; missing identity throws
  `UnauthorizedError` (401). Don't hand-roll
  `CurrentUserHolder.get().orElseThrow(... ConflictError / ForbiddenError /
  UnprocessableError ...)` — picks the wrong status. If a method shadows
  the field (`CurrentUser caller = caller.requireUser()`), rename the local
  to `actor`.
- **Three on-behalf authorities on attendance submits, and only two of
  them auto-approve.** `OnBehalfResolver` (leave / short leave / OT /
  regularization, `onBehalfOf` in the body) resolves, in order: `attendance.approval.override`
  → `HR_OVERRIDE` (whole chain auto-approved); target's direct manager →
  `MANAGER_ON_BEHALF` (MANAGER stage auto-approved, advances to HR); the
  matching `team.request.generation.<type>` permission **plus same
  `department_id` as the caller** → `TEAM_ON_BEHALF`, which auto-approves
  **nothing**. Gate auto-approval on `Resolution.autoApproves()`, not
  `isOnBehalf()`.
  **`TEAM_ON_BEHALF` anchors the MANAGER stage on the *caller*, not the
  target** — a clerk files for their whole department and their own
  department head approves, so `openChain` takes
  `Resolution.approvalAnchorEmployeeId()` while the request row takes
  `targetEmployeeId()`. Those two are equal in every other mode; conflating
  them silently routes a clerk's submissions to each target's own manager
  instead. Bulk OT is not an on-behalf path and still anchors per target.
- **EMPLOYEE role baseline is `attendance.read`, `attendance.write`,
  `travel.read`, `travel.write` — and that is the whole list.** Every other
  ESS surface (own payslips, advances, chat, notifications,
  `/employees/me`, `/picker`, `/by-ids`) is `isAuthenticated()` and
  self-scoped server-side, deliberately. EMPLOYEE is attached automatically
  at ESS provisioning (`OnboardingSaga` / `EssGrantSaga`), so any other role
  is additive on top of it — `TEAM_REQUEST_GENERATION` carries only its
  eight `team.request.generation.*` codes and relies on EMPLOYEE for
  `attendance.write`. Don't grant `employee.read` or `payroll.read` to
  EMPLOYEE to "fix" a 403; the right fix is an auth-only self-scoped
  endpoint.
- **Paginated controllers cap page size** with
  `PageableDefaults.apply(pageable, "<default-sort-field>")` from
  `commons-web`. Without it, `size=100000` OOMs the pod. The default-sort
  string is the entity field used when caller didn't send one.
- **`POST /…/{id}/delete` (or `/stop`, `/cancel`) when a delete needs a
  body.** Java HttpClient pre-21, Cloudflare, and some intermediaries strip
  bodies from `DELETE`. Body-less deletes stay `DELETE`; anything with a
  reason / options / confirmation goes through `POST`.
- **Catalog admin services emit audit on every mutation.** Holiday,
  leave-type, shift, approval-delegation, etc. call
  `audit.emit("<entity>", id, "<action>", before, after, false)` on every
  create/update/deactivate/delete. Use a small `snapshot(...)` helper
  returning the fields to diff; `null` for `before` on creates and `after`
  on hard deletes.
- **OpenAPI / Swagger UI:** every service depending on `commons-web`
  exposes `/v3/api-docs` and `/swagger-ui.html`. Defaults (try-it-out,
  filter, alpha sort, persisted auth, request duration) live in
  `commons-web`. Per-service contract:
    1. Set `hrms.openapi.title/description/contact-name/contact-email/server-url/server-description`
       in `application.yml`.
    2. Whitelist swagger paths in the security chain: `/v3/api-docs`,
       `/v3/api-docs/**`, `/v3/api-docs.yaml`, `/swagger-ui.html`,
       `/swagger-ui/**`, `/swagger-resources/**`, `/webjars/**`.
    3. Annotate every `@RestController` with `@Tag` and every handler with
       `@Operation`. Add `@ApiResponse` for non-200 cases the FE handles
       (401, 404, 409, 422).
    4. Annotate request/response DTO records with `@Schema` on the type and
       each component.
    5. Unauthenticated endpoints (login, refresh, logout, JWKS, webhooks)
       put `@SecurityRequirements` (no value) on the controller class to
       drop the bearer lock icon.
  Customizations go in a `@Bean OpenAPI` that customizes the autoconfig
  bean — don't fork the autoconfig.
- **Document uploads use a two-step signed-PUT flow.** Client gets
  pre-signed PUT URL → uploads to MinIO → POSTs metadata back. Service
  `stat()`s the object before persisting (422 if missing) and refuses any
  object key outside the calling employee's prefix (`employees/{id}/...`).
  One bucket per concern (`employee-docs` first), created on startup via
  `hrms.storage.ensure-buckets`. URL TTLs configurable per service. Don't
  introduce a multipart-streaming endpoint.
- **Per-year sequence allocator.** For human-readable year-scoped IDs
  (`FS-2026-0042`), use a `<thing>_sequence(year, last_value)` table with
  `INSERT … ON CONFLICT (year) DO UPDATE … RETURNING last_value` inside
  the business tx. Postgres' row-level lock serializes concurrent
  allocators; no advisory lock or sequence object needed. Worked example:
  `EmployeeCodeService`.
- **Pakistan payroll: no FBR slab math, no salary structures/templates.**
  Each employee is `employee_salaries.basic` (only taxable component) plus
  per-employee picks from a flat `salary_components` catalog (allowances +
  deductions, all flat amounts — no percentages, no formulas). Income tax
  is a deduction line (`code='INCOME_TAX'`) HR maintains. EOBI is
  system-level monthly, deducted only when `eobi_enrolled` is on. PESSI is
  **employer-only** — `min(gross, cap) × rate`, recorded as employer cost
  (no impact on net). Fiscal year July–June, in `fiscal_years` with exactly
  one `CURRENT` row; every `payroll_runs` references it.
- **BFF aggregates across services, not within one.** A composite whose
  downstream calls all hit one service belongs in that service. After
  `docs/caching.md` Steps 3–5, BFF holds only genuinely cross-service
  composites: `/bff/employee/{id}` (employee + audit), `/bff/employees/{id}/timeline`
  (employee + audit), `/bff/account/{summary,profile}` (auth + audit + employee),
  `/bff/admin/users/{id}/overview` (auth + audit), `/bff/attendance/team/{today,month}`
  (employee + attendance via `ManagerCoverageResolver`),
  `/bff/timeline/{requestType}/{id}` (attendance + audit),
  `/bff/employees/by-ids` (generic identity resolver), `/bff/dashboard/**`,
  `/bff/search`, `/bff/notifications/stream` (SSE), `/bff/chat/**`. Single-
  service composites previously in BFF (`PayrollBff*`, six `EmployeeBff*`,
  eight `AttendanceBff*`, `ReportCatalog*`) live in their owning service or
  on the FE. Pass-through endpoints don't exist — FE goes through the
  gateway. BFF response DTOs come from `<svc>-api` modules, not redeclared
  `View` records under `bff/client/dto/`.
- **Display fields cross schema via the shared cache, not denorm columns
  or HTTP fan-out.** When another service's read paths frequently need a
  foreign-key target's display fields (`employeeCode`, `fullName`,
  `departmentCode`, `designationCode`, `photoUrl`), use
  `EmployeeDisplayCache.getMany(ids)` and `CatalogCache.snapshot()` from
  `commons-cache`. The cache is populated by an `employee.events` consumer
  in every cache-using service (different group ids; idempotent writes;
  trivial CPU cost) and falls through to employee-service via HTTP on
  Redis-down. Composite endpoints in payroll / attendance use it directly;
  BFF cross-service composites use it. Common cases: payroll (payslips,
  advances, off-cycle, increments, bonuses), attendance (records, punches,
  operator dashboard, approvals inbox), and any surface keyed by employee
  id without names attached. Doctrine in `docs/caching.md`.
- **`actorLabel` rides the JWT, not the BFF.** The auth-service JWT carries
  the user's display name as the `name` claim. Gateway's
  `JwtAuthenticationFilter` reads it into `CurrentUser.fullName`,
  `IdentityHeaderInjectionFilter` forwards it as `X-User-FullName`,
  downstream `HeaderIdentityFilter` re-parses, and `EventEnvelope.wrap`
  threads it onto every emitted event as `actorLabel`. Audit-service stores
  it on the row; BFF reads it directly. No more
  `auth /users/by-ids` resolver per timeline page.
- **Promote to `commons-*` after the third caller, not the first.** Two
  duplicates of an eight-line helper are cheaper than a commons module with
  one user. Fewer than three real callers — leave it inlined.
- **`attendance_employees.employee_code` = exactly what the device's ZK
  protocol returns as `user_id`.** Devices report unpadded numerics
  (`13411`); never write `013411` into the bridge — lookup is by string
  equality, not numeric. If you regenerate the bridge from any source,
  strip leading zeros first.
- **`DevicePunchReplayJob` knobs are env-overridable through
  `application.yml`.** `hrms.attendance.poller.*` properties are wired as
  `${ENV_VAR:default}` placeholders in
  `services/attendance/src/main/resources/application.yml`. The YAML wiring
  is load-bearing — see the `@Scheduled` gotcha below. Defaults:
  `replay-interval-ms=60000`, `replay-batch-size=10000`,
  `replay-initial-delay-ms=60000`, `unmatched-max-attempts=8`.
- **Polling a ZK device via pyzk's read commands does NOT clear the
  device's attendance log.** Empirically verified across both firmware
  families (Ver 6.60 SPA-UI and the older `/csl/login`). If a device shows
  `att=0` after a poll, the cause is on-device ("Auto Delete on Sync",
  manual keypad clear, or another LAN tool). Don't suspect our code first.
- **Reports.** PDF via JasperReports CE, Excel via Apache POI (`poi-ooxml`,
  SXSSF for >2k rows), CSV via `commons-reports.CsvWriter`. Templates under
  `services/<svc>/src/main/resources/jasper/<name>.jrxml` (runtime
  compiled; entry point `JasperRenderer.renderPdf`). Reports live in the
  **owning service** (the one with the data) as
  `service/<Name>ReportService.java` + `controller/ReportsController.java`
  mapped at `/reports/...`. Cross-service composites only go in BFF; no
  separate reports service. Doctrine in `docs/reports.md`. Async jobs go
  through `report_jobs` + `ReportJobRunner` poller (payroll today;
  attendance/employee can stand up their own runner the same way). Outputs
  land in MinIO `reports/<svc>/<…>` (in `hrms.storage.ensure-buckets`); job
  download endpoint 302s to a signed URL. Permissions `<svc>.reports.run`
  and `<svc>.analytics.read`. Every run emits an audit row
  (`entity="report"`, `action="<name>.run"`); statutory reports
  (EOBI/PESSI/tax) are `userVisible=true`.
- **Gateway routes `/reports/**` split by owner.** Add explicit per-owner
  routes in `services/gateway/src/main/resources/application.yml`. Generic
  paths (`/reports/jobs/**`) route to whichever service hosts `report_jobs`
  — payroll today.

## Local dev environment

- **Postgres on the host** (Postgres.app on Mac), NOT in compose.
  `postgres:postgres@localhost:5432/fshrms`. Six schemas pre-created. `psql`
  at `/Applications/Postgres.app/Contents/Versions/latest/bin/psql` — not
  on PATH.
- **Everything else in `docker-compose.yml`:** Redis (`localhost:6379`),
  RedisInsight (http://localhost:5540), Kafka (`localhost:9092` from host,
  `kafka:9094` inside compose network), Kafka UI (http://localhost:8090),
  MinIO (`localhost:9000` API, http://localhost:9001 console,
  `minioadmin:minioadmin`).
- **apache/kafka image quirks** (locked; don't undo): CONTROLLER and
  INTERNAL listeners bind to hostname `kafka`, not `0.0.0.0` (image rejects
  `0.0.0.0` in advertised.listeners and crash-loops). Cluster ID must be a
  22-char base64 UUID. Inside the kafka container, CLI tools use
  `--bootstrap-server localhost:9092` (the EXTERNAL listener);
  `localhost:9094` won't connect because INTERNAL binds to hostname
  `kafka`.
- **`mem_limit: 2g` on every Java service in `docker-compose.yml`.**
  Without it, JVM `-XX:MaxRAMPercentage=75` reads the full ~12GiB Docker
  Desktop VM and grows toward ~9GiB. With 9 services racing the same VM,
  the OOM-killer eventually picks the largest victim. 2g per service ⇒
  1.5g heap, bounded.

## Build / framework gotchas

Hard-won. Don't make a future Claude or teammate rediscover them.

- **SB4 split `spring-boot-autoconfigure` into per-feature jars.** Most
  autoconfig classes moved from
  `org.springframework.boot.autoconfigure.<feature>.…` into
  `org.springframework.boot.<feature>.autoconfigure.…` (e.g.
  `WebMvcAutoConfiguration` → `org.springframework.boot.webmvc.autoconfigure`,
  `DefaultKafkaProducerFactoryCustomizer` →
  `org.springframework.boot.kafka.autoconfigure`). When an old import
  doesn't resolve, look in the matching `spring-boot-<feature>` jar.
- **Never look an employee up by `GET /employees/{id}` from another
  service on a user-facing path.** It requires `employee.read`, which only
  HR / ADMIN hold — MANAGER lost it in auth `V0024`, and
  ATTENDANCE_OPERATOR, TEAM_REQUEST_GENERATION and EMPLOYEE never had it.
  The 403 comes back inside a `RestClientException`, gets wrapped, and the
  user sees a bare `502 UPSTREAM_EMPLOYEE_FAILED` with the real cause only
  in the caller's logs. Use `GET /employees/by-ids`, which is
  `isAuthenticated()` and returns the same `EmployeeDto` shape (including
  `managerUserId`). Attendance's `EmployeeClient.getById` /
  `getByIdOptional` are already implemented on top of it — keep them that
  way. Same trap bit on-behalf submits (`OnBehalfResolver`), approval-chain
  assignee resolution (`AttendanceManagerResolver`), bulk OT
  (`OvertimeService.bulk`) and operator punch
  (`PunchService.recordOperatorPunch`).
- **Tests that walk the source tree must normalise path separators.**
  `PermissionRegistryTest.javaSources` filtered on
  `p.toString().contains("/src/main/java/")`. On Windows `Path.toString()`
  uses backslashes, so the filter matched nothing, the scan found zero
  files, and `everyEnforcedPermissionIsKnown` passed **vacuously** — it had
  stopped guarding anything, silently, while staying green. It was hiding
  16 enforced-but-uncataloged permissions (`travel.*`, `canteen.*`,
  `employee.tours.*`). Use `replace(File.separatorChar, '/')` in any such
  filter, and treat "this assertion has never failed" as a reason to check
  it still runs.
- **JPA `@EntityListener` instances are not Spring-managed** — no DI. The
  audit listener reads from static thread-local holders (`CurrentUserHolder`,
  `RequestContext`) populated by the web filter. Don't try to `@Autowired`.
- **`Outbox.publish` is `Propagation.MANDATORY`** — must run inside a
  caller `@Transactional`. Same commit boundary as the business write.
- **Outbox migration ships in `commons-kafka`.** Outbox-using services
  include it: `spring.flyway.locations: classpath:db/migration,classpath:db/commons-outbox`.
- **`spring.flyway.out-of-order: true`** is required on services that pull
  commons migrations. Commons uses V05xx, services use V000x. Without it,
  adding a new V000x after V0500 (commons-saga) is applied fails startup
  with "Detected resolved migration not applied". Already set on auth,
  attendance, employee, payroll.
- **The env role needs `CREATE` on the DATABASE, not just its schemas.**
  Flyway runs as the env role (`prod` / `uat`) and
  `V0501__employee_trgm.sql` does `create extension if not exists pg_trgm`.
  `pg_trgm` is a **trusted** extension so no superuser is involved, but
  creating any extension still requires `CREATE` on the database — a
  privilege owning a schema does not confer. Without it employee-service
  dies at startup with `permission denied to create extension "pg_trgm"`.
  Fix: `GRANT CREATE ON DATABASE <env> TO <env>`. Note `03` creates the DB
  `OWNER <env>`, which would have granted this implicitly — on a host where
  the database was made by hand, check
  `SELECT pg_get_userbyid(datdba) FROM pg_database WHERE datname=...`.
- **`DROP SCHEMA employee CASCADE` drops `pg_trgm` with it.** The extension
  is created `with schema employee` so the search path can stay scoped, so
  it is owned by that schema, not global. Any schema-level reset must
  therefore leave the role able to recreate it (see the bullet above).
  `pgcrypto` is unaffected — it lives in `public`.
- **Per-service Hibernate schema.** Each service sets
  `spring.jpa.properties.hibernate.default_schema=<schema>` so unmapped
  entities (notably the outbox) land in the right schema without per-entity
  `@Table(schema=…)`.
- **`commons-test` is `compile`-scoped on purpose.** It IS the test support
  library; services depend on it as `<scope>test</scope>`. Don't repackage
  as a `test-jar`.
- **`EnvironmentPostProcessor` uses `META-INF/spring.factories`, not
  `.imports`.** SB3.4 introduced `META-INF/spring/<FQCN>.imports` for
  `@AutoConfiguration` discovery (SB4 keeps it), but
  `EnvironmentPostProcessor`, `ApplicationListener`,
  `ApplicationContextInitializer`, and other `SpringFactoriesLoader`-loaded
  hooks still come from `spring.factories`. The `.imports` form silently
  no-ops.
- **`RestClient.Builder` autowiring needs `spring-boot-starter-restclient`
  in SB4** — split out from `spring-boot-autoconfigure`. `commons-web`
  already pulls it in. **Pattern for new HTTP clients:** inject
  `RestClient.Builder`, call `.baseUrl(props.url()).build()`. The
  commons-web `RestClientCustomizer` (HardeningAutoConfiguration) pre-wires
  `IdentityForwardingInterceptor` + `CorrelationIdPropagatingInterceptor`.
  Saga-thread callers (no inbound request to copy `X-User-*` from) take an
  explicit identity map and apply it via `.headers(h -> ...)` per call —
  see `services/employee/.../client/AuthClient.java`.
- **`@EntityScan` / `@EnableJpaRepositories` package paths must match
  reality after a refactor.** Spring Data fails late ("No qualifying bean
  of type 'XRepository'" at startup) if `basePackages` points at a renamed
  package. After any rename, grep `*Application.java` and any
  `@EnableJpaRepositories` / `@EnableRedisRepositories` / `@EntityScan`.
  Default to scanning the whole base package (omit `basePackages`); only
  outbox needs the extra `com.forward.hrms.commons.kafka.outbox` on
  `@EntityScan`.
- **Springdoc on Spring Boot 4: use the 3.x line (pinned 3.0.3).** springdoc
  2.x targets Spring Boot 3 / Jackson 2; 3.x targets Spring Boot 4 / Spring
  Framework 7 / Jackson 3. Staying on 2.8.13 under SB4 was a cross-line
  mismatch: the SB4 BOM forced `swagger-annotations-jakarta` to 2.2.47
  (whose `@Schema.defaultValue()` defaults to the `##default` sentinel)
  while springdoc 2.8.13 shipped swagger-core 2.2.36, which didn't strip
  that sentinel — so Swagger UI rendered every request body and enum query
  param as the literal `"##default"`. 3.0.3 brings its own consistent
  swagger-core family (all 2.2.47), fixing it with no swagger-core pin.
  Pinned in `services/pom.xml` via `${springdoc.version}`. (3.x still
  honors the `springdoc.swagger-ui.*` overrides from `commons-web` —
  verified on `/v3/api-docs/swagger-config`.)
- **Maven incremental compile + IDE-broken `.class` files.** Some IDEs
  (Eclipse JDT-LS / VS Code Java) write compilation-error-marked `.class`
  files into `target/classes` while their classpath model lags a recent
  `pom.xml` change. Maven's `compile` sees the `.class` is newer than
  `.java`, skips recompile, and ships a fat jar that throws `java.lang.Error:
  Unresolved compilation problems` at runtime. Symptom: "BUILD SUCCESS but
  app won't start." Fix: `clean package` after IDE pom edits; build outside
  the IDE if it keeps re-poisoning the dir.
- **Spring relaxed-binding does NOT propagate OS env vars to kebab-case
  property names inside a `@Scheduled(fixedDelayString=…)` annotation.**
  Setting `HRMS_FOO_BAR_BAZ_MS` for `foo.bar-baz-ms` works for `@Value`
  fields, but @Scheduled silently uses the default (no error logged — the
  symptom is total silence on `scheduling-1`). Wire env overrides through
  `application.yml` as `${HRMS_FOO_BAR_BAZ_MS:default}` so Spring resolves
  at YAML parse and stores under the canonical kebab key. Worked example:
  `services/attendance/src/main/resources/application.yml`
  `hrms.attendance.poller.*`.
- **Hibernate's session auto-flush is O(N²) on big batches.** A 10k-row
  `ingestBiometricBatch` originally took ~12 minutes (per-row cost grew
  from ~31ms to several hundred ms). Fix:
  `entityManager.flush(); entityManager.clear()` every ~500 rows. Drops
  persist to ~100s. See `PunchService.ingestBiometricBatch`.
- **Per-row recompute kills batch performance.** `PunchService.savePunch`
  ends with `recompute.recompute(employeeId, workDate)`. Batched callers
  (replay job, bulk import) use `savePunchNoRecompute` and collect distinct
  `(employeeId, workDate)` pairs in a `Set<DayKey>`, then run one recompute
  per pair at the end. Recompute is idempotent on a day; this drops
  recompute work 20–200×.
- **`PunchUnmatchedRepository.findResolvable(Pageable)` is paginated for a
  reason.** Don't call without a bound — a multi-month backlog OOMs the
  pod before it reaches `deleteByIdIn`. Default page size (10000)
  configured via `hrms.attendance.poller.replay-batch-size`.
- **`attendance-poller`'s `pull.py` main loop wraps `_tick(svc, settings)`
  in a top-level try/except.** Without it, a transient attendance-service
  outage propagates out of `while True`, the process exits, and `restart:
  unless-stopped` creates a tight ~90s restart loop. `_tick` already
  swallows per-device errors; the top-level wrapper covers calls outside
  that loop (poller_targets, etc.).
- **`@Qualifier("outboxObjectMapper")` on services that inject
  `ObjectMapper`.** Both `commons-kafka` and `commons-saga` register named
  `ObjectMapper` beans. A new `@RequiredArgsConstructor` taking
  `private final ObjectMapper json` fails context init with "expected
  single matching bean". Drop the Lombok ctor for that class, write the
  ctor by hand, and qualify on `outboxObjectMapper` (or `sagaObjectMapper`
  inside a saga step).
- **`@Transactional(readOnly = true)` is incompatible with
  `AuditEmitter.emit`.** The emit calls `Outbox.publish`
  (Propagation.MANDATORY) which joins the read-only tx, then `INSERT` into
  `<schema>.outbox` fails with "cannot execute INSERT in a read-only
  transaction". Either drop `readOnly`, or call `audit.emit(...)` outside
  the read-only block.
- **JasperReports' `JRBeanCollectionDataSource` does not understand Java
  `record` accessors.** The bundled Apache Commons BeanUtils (1.9.4)
  reflects on `getXxx`/`isXxx`; record components expose `xxx()`, so the
  data source throws `Unknown property '<name>'` on the first row with a
  value. Use `JRMapCollectionDataSource` with `List<Map<String,?>>` for
  new templates. The empty-collection case silently renders an empty PDF —
  don't take "report runs" as proof the data path is correct.
- **MinIO signed URLs bake the host into the SigV4 signature.** Signing
  with `endpoint=http://minio:9000` (in-cluster) and then having a client
  swap `minio` for `localhost` returns 403 — silent signature mismatch.
  For local dev, use `curl --resolve minio:9000:127.0.0.1` or add
  `127.0.0.1 minio` to `/etc/hosts`. Long-term fix is separating
  in-cluster vs presign endpoints (not done).
- **`commons-cache` autoconfig needs `@AutoConfiguration(afterName = …)`.**
  Its `@ConditionalOnBean(StringRedisTemplate)` evaluates before
  `DataRedisAutoConfiguration` runs by default in SB4, so the cache beans
  silently no-op. Wire `afterName = { "org.springframework.boot.data.redis.autoconfigure.DataRedisAutoConfiguration",
  "org.springframework.boot.jackson.autoconfigure.JacksonAutoConfiguration" }`
  on every commons-* autoconfig that has a `@ConditionalOnBean` ordering
  dependency. SB4 also moved the Jackson autoconfig FQCN — note the
  per-feature path.
- **Jackson 3 vs 2 in commons modules.** SB4 auto-registers a Jackson 3
  `ObjectMapper` (different package — `tools.jackson` vs
  `com.fasterxml`). When a commons module ships its own Jackson 2
  `ObjectMapper` for serializing event payloads, mark it
  `defaultCandidate = false` and `@Qualifier`-it on the consumer side, or
  the autowire collides with the SB4 default. Already done for
  `outboxObjectMapper`, `sagaObjectMapper`, `cacheObjectMapper`, and
  `idempotencyObjectMapper`.
- **JPA-less services that pull `commons-cache` need
  `hrms.outbox.enabled=false`.** `commons-cache` depends on
  `commons-employee-events` → `commons-kafka`, which puts
  `OutboxAutoConfiguration` on the classpath. The autoconfig is gated on
  `@ConditionalOnClass(KafkaTemplate, jakarta.persistence.Entity)` and
  defaults enabled — so a JPA-less service (BFF, gateway) blows up at
  startup with "required a bean named 'entityManagerFactory'". Set
  `hrms.outbox.enabled=false` in `application.yml`.

## Deployment (one VM per environment)

Topology in `docs/server-setup.md`; setup scripts and platform manifests in
`server-setup/`.

- **UAT and Prod are separate VMs, sharing nothing.** Each host runs its
  own Postgres, Redis, Kafka, MinIO, `registry:2`, Jenkins, k3s, and
  observability stack. No shared broker, no shared registry, no shared
  controller. Consequences: topic names and bucket names stay unprefixed
  (there is no second environment on the box to collide with), and a
  sealed secret cannot move between the VMs — each cluster's
  sealed-secrets controller has its own keypair.
- **Image tag = `${GIT_SHORT_SHA}` only.** Never `:latest`. The tag names
  the source commit. Each host pushes to its own registry —
  `10.10.100.50:5001/<svc>:<sha>` on UAT, `<PROD_VM_IP>:5001/<svc>:<sha>`
  on Prod.
- **Prod builds from source; it does not copy UAT's image.**
  `Jenkinsfile-prod` builds the head of `main` by default and runs the same
  build/push/deploy stages as UAT against the prod registry and namespace.
  **Prod deploys the `prod` branch and nothing else.** `Jenkinsfile-prod`
  pins `*/prod`, and JCasC reads the pipeline definition from the same
  branch so the two can't drift. The jobs take **no parameters** — promote
  by merging into `prod`, roll back by moving `prod` to the older commit and
  re-running. `PROMOTE_SHA` is gone; don't reintroduce it. A dedicated prod
  host with zero dependency on the UAT host was worth more than
  byte-identical images. Don't reintroduce a cross-VM `docker pull`.
- **Host-specific values live in overlays, never in `k8s/base/`.**
  `DB_HOST`, `REDIS_HOST`, `KAFKA_BROKERS`, `MINIO_ENDPOINT`,
  `POLLER_BASE_URL`, `HRMS_OPENAPI_SERVER_URL`. A base holding one
  environment's IP is how prod ends up pointed at UAT's database.
- **Per-service Kustomize layout.** Base under `services/<svc>/k8s/base/`:
  `deployment.yaml`, `service.yaml`, `configmap.yaml`,
  `servicemonitor.yaml`, `prometheusrule.yaml`, `kustomization.yaml`.
  Overlays at `services/<svc>/k8s/overlays/{uat,prod}/`:
  `kustomization.yaml`, `deployment-patch.yaml` (replicas/resources),
  `sealed-secret.yaml`. **No `images:` block in the base** (see Kustomize
  gotcha). **No Ingress per service** — single Traefik IngressRoute in
  `server-setup/k8s/ingress/`.
- **k3s `jenkins-deployer` SA RBAC scope.** Built-in `edit` ClusterRole
  does NOT cover `bitnami.com/sealedsecrets` or
  `monitoring.coreos.com/{servicemonitors,prometheusrules,podmonitors}`.
  Each env namespace needs two extra Roles + RoleBindings
  (`jenkins-deployer-sealedsecrets`, `jenkins-deployer-monitoring`)
  granting full verbs. Without them, pipelines apply
  Deployment/Service/ConfigMap fine then fail Forbidden on the CRDs.
  `traefik.io/ingressroutes` is deliberately **not** granted — the app routes
  (`app.*` → web, `api.*` → gateway) come from
  `server-setup/k8s/ingress/web-ingress.yaml` via `06`, not from a service
  overlay. Consequence: if the env namespace is ever wiped, a Jenkins deploy
  restores every workload but **not** the two IngressRoutes; re-apply that
  manifest by hand with the placeholders substituted. Check with
  `kubectl auth can-i create ingressroute.traefik.io -n <ns>
  --as=system:serviceaccount:<ns>:jenkins-deployer`.
- **Sealed-secret content per service.** Each overlay's `sealed-secret.yaml`
  carries `DB_USER` + `DB_PASSWORD`, `SPRING_DATA_REDIS_PASSWORD` for
  Redis-using services (auth, attendance, notification, chat, bff —
  Spring's Redis health indicator forces readiness DOWN on auth failure),
  plus per-service knobs (`HRMS_AUTH_BOOTSTRAP_ADMIN_PASSWORD`, MinIO keys
  for employee/payroll/chat). Generate on the target host, against that
  cluster's own cert:
  `kubectl create secret … --dry-run=client -o yaml | kubeseal --cert ~/server-setup/k8s/platform/sealed-secrets-<env>.pub.pem -o yaml`.
- **Every service runs exactly one replica, and that is load-bearing.** Not a
  capacity guess — three things in the code assume a single poller per
  service. `OutboxRepository.findReadyToSend` has no `FOR UPDATE SKIP
  LOCKED`, so two pollers read the same PENDING rows and publish every event
  twice. `SagaRunner` says the same in its own javadoc. And no `@Scheduled`
  job (`NightlyCloseJob`, `LeaveRenewalJob`, `ResignationCompletionJob`,
  `AuditPartitionService`, `RetentionService`, `DevicePunchReplayJob`,
  `ReportJobRunner`) is guarded by ShedLock or leader election, so they all
  double-fire. Scaling `audit` is additionally a no-op: consumer-group
  parallelism is capped by partition count and the `<svc>.audit` topics are
  1-partition, so a second pod idles — and `ReplayService` holds replay
  tokens in an in-process map. On single-node k3s a second pod adds no
  capacity anyway; it splits the same cores. **Scale vertically** — raise
  `limits` in the prod `deployment-patch.yaml`. Prereqs before any service
  goes to 2: skip-locked on the outbox + saga queries, a lock row on the
  crons, gateway rate-limit buckets and audit replay tokens into Redis,
  repartitioned topics. Not worth it until there is a second node.
  (`replicas: 1` costs no deploy downtime: maxUnavailable rounds to 0 and
  maxSurge to 1, so the new pod is ready before the old one dies.)
- **k3s registry trust.** `/etc/rancher/k3s/registries.yaml` lets pods
  pull insecurely from the **local** registry (`10.10.100.50:5001` on UAT,
  `<PROD_VM_IP>:5001` on Prod). Pods never pull across hosts. If you ever
  stand up fresh k3s, that file must land before the first app pod
  schedules.

## Deployment gotchas

Hard-won.

- **JCasC `${...File:path}` interpolators are typo-sensitive.** Plugin
  supports `${readFile:path}` (text) and `${readFileBase64:path}` (base64).
  `${base64File:...}` looks plausible but **silently expands to empty** —
  Jenkins logs nothing. Symptom: a pipeline using the file credential as
  `KUBECONFIG` finds an empty file; kubectl with empty `--kubeconfig` falls
  back to `localhost:8080` (hits Jenkins itself, returns HTML
  auth-redirects). Working spelling in `server-setup/jenkins/casc.yaml`.
- **Kustomize image transforms REPLACE across overlays — they don't
  merge.** If base ships `images: [{name: auth, newName: registry/auth}]`
  and overlay ships `images: [{name: auth, newTag: abc123}]`, the overlay
  wins wholesale and `newName` is dropped → ImagePullBackOff. Fix: keep
  `images:` only in overlays, with both `newName` AND `newTag`.
- **`newTag:` must be quoted.** Short git SHAs can be all digits
  (`2782146`). YAML parses unquoted as int and kustomize errors with
  `cannot unmarshal number into Image.newTag string`. Templates ship
  `newTag: "GIT_SHA_PLACEHOLDER"`.
- **`kustomize edit set image` chokes on multi-colon registry URLs.** The
  CLI parser treats colons as tag separators, so
  `auth=10.10.100.50:5001/auth:abc123` (three colons) is misparsed and
  `newTag` ends up unset → `:latest`. Use a literal placeholder + `sed`
  instead.
- **Jenkins runs as the `jenkins` user with its own `$HOME`.** Doesn't
  share `~/.ssh/known_hosts` with `hrm`. Pre-populate
  `/var/lib/jenkins/.ssh/known_hosts` with `ssh-keyscan github.com` before
  the first SCM clone, or builds hang at host-key verification. Credential
  files under `/etc/jenkins/credentials/` must be `jenkins:jenkins`,
  mode 600.
- **`management.endpoint.health.group.readiness.include` is `*`, not
  `db,redis`.** Hardcoding indicator names fails startup on services
  without a DataSource (gateway, bff): `Included health contributor 'db'
  does not exist`. `*` includes whatever is present. Already in
  `commons-web/HardeningDefaultsEnvironmentPostProcessor`.
- **Hardening defaults ship via `EnvironmentPostProcessor`, not
  `application.yml`.** Spring Boot's classpath `application.yml` lookup
  picks the first match — each service ships its own, so a
  `commons-web/.../application.yml` would be silently shadowed. Defaults
  (`server.shutdown=graceful`, `management.server.port=9090`, actuator
  surface, tracing endpoint, structured-log toggle, HikariCP pool size
  + leak-detection-threshold) added with lowest precedence by
  `HardeningDefaultsEnvironmentPostProcessor`, registered in
  `META-INF/spring.factories`. Same pattern as
  `SwaggerUiDefaultsEnvironmentPostProcessor`.
- **HikariCP default `maximum-pool-size=10` is not enough** once a JPA
  service runs outbox + Kafka listener + `@Scheduled` REQUIRES_NEW
  batches concurrently. Symptom: `Connection is not available, …
  active=10, idle=0, waiting=N` and `idle` PG backends sitting on an
  old `COMMIT` (Java thread holds the connection wrapper without
  closing). Hardening defaults now ship `maximum-pool-size=20` and
  `leak-detection-threshold=60000` so the next leak logs the
  acquiring stack trace. Override via `HIKARI_MAX_POOL_SIZE` /
  `HIKARI_LEAK_DETECTION_THRESHOLD_MS`. 20 × 7 JPA services = 140,
  under PG `max_connections=200`.
- **UAT Postgres direct access from the VM:**
  `PGPASSWORD=uatpassword psql -h 127.0.0.1 -p 5432 -U uat -d uat`. DB
  only accepts `127.0.0.1` and the k3s pod CIDR (`10.42.0.0/16`) per
  `pg_hba.conf`; connecting via the external IP `10.10.100.50` returns
  `no pg_hba.conf entry`. From inside a pod, host is `10.10.100.50`
  (configmap `DB_HOST`). Same credential lives in `attendance-secret`.
- **`audit` kafka listener concurrency must match topic partition count.**
  Dev/UAT `<svc>.audit` topics are 1-partition; the prior `concurrency=4`
  spawned 5 listener groups × 4 = 20 KafkaConsumer threads per audit pod,
  15 idle but heartbeat-spamming the broker (kafka idle-CPU 70-80% in
  local dev). Default is now 1; bump `SPRING_KAFKA_LISTENER_CONCURRENCY`
  only after repartitioning the audit topics.

## Observability

Locked-in choices for the Grafana / Prometheus / Loki / Zipkin stack in the
`observability` namespace. Operator usage in `docs/observability.md`.

- **Datasource UIDs are pinned** (`prometheus`, `alertmanager`, `loki`,
  `zipkin`) — referenced from every dashboard, derived field, and
  trace-to-logs link. Loki/Zipkin use `deleteDatasources` in
  `kps-values.yaml` so a helm upgrade can replace existing rows whose UIDs
  were auto-generated. Don't switch to `additionalDataSources` —
  append-only, can't change UIDs in place.
- **Cross-datasource links wired in datasource config, not per dashboard.**
  Loki has a `derivedFields` rule turning `traceId` from JSON logs into a
  Zipkin link. Zipkin has `tracesToLogsV2` pointing back at Loki filtered
  by traceId. Both fall out of `kps-values.yaml`.
- **Dashboards ship as ConfigMaps with `grafana_dashboard=1`.** Bundled
  via `configMapGenerator` in
  `server-setup/k8s/platform/observability/dashboards/`. The Grafana
  sidecar polls every namespace for that label and reloads automatically.
  Three FSHRMS-owned: `fshrms-overview` (cluster-wide RED + JVM),
  `fshrms-service-detail` (HTTP/JVM/HikariCP/Kafka/logs), `fshrms-logs`
  (Loki explorer). Don't bake dashboards into chart values.
- **Per-service `PrometheusRule` carries 5 alerts.** `<Svc>HighErrorRate`
  (5xx > 5%), `<Svc>Down` (up == 0), `<Svc>HighLatency` (p95 > 1s),
  `<Svc>JvmHeapPressure` (used/committed > 90%),
  `<Svc>HikariConnectionStarvation` (pending > 0). Rules for metrics a
  service doesn't emit (Hikari on gateway/bff) silently no-op.
- **Tracing sampling is 1.0 in cluster, 0.0 locally.** Set per-env via
  `MANAGEMENT_TRACING_SAMPLING_PROBABILITY`.
- **Logs are JSON one-line per record only inside containers.**
  `LOGGING_STRUCTURED_FORMAT_CONSOLE=logstash` (per-service ConfigMap)
  flips SB4 structured logging on. Local dev keeps the human-readable
  pattern.
- **Grafana persistence and the `initChownData` busybox.** With a PVC,
  Grafana writes pdf/png/csv subdirs the chart's chown-init can't re-own
  ("Permission denied" even as root, because the GID drifted).
  `initChownData.enabled: false` in `kps-values.yaml` skips it; runtime
  UID hasn't changed, so the data is readable. Don't toggle back without
  bumping the chart and verifying init succeeds on re-deploy.
- **Two suffixes, and the split is a security boundary, not cosmetics.**
  `HOST_SUFFIX` is public (`hrms.forward.pk` on prod) and carries the
  frontend at the bare suffix plus the gateway at `api.<suffix>`, over TLS.
  Prod drops the `app.` prefix because that name is what a user types;
  every other env keeps it. `06` computes `APP_HOST`/`API_HOST`.
  `INTERNAL_SUFFIX` is always `<env>.hrms.local`
  and carries grafana/prometheus/alertmanager/zipkin over plain HTTP, LAN
  only, mapped in the operator's `/etc/hosts`. **Don't give the
  observability hostnames public DNS records** — only Grafana has a login;
  Prometheus, Alertmanager and Zipkin are unauthenticated, so a public
  record exposes every metric and trace and lets anyone silence alerting.
  Both defaults come from `ENV_NAME` in `06-platform-up.sh`; either can be
  overridden in `.env`.
- **`.local` for internal UIs is load-bearing, not legacy.** `forward.pk`
  sends `Strict-Transport-Security: max-age=31536000; includeSubDomains`,
  so a browser that has ever loaded the company site force-upgrades *every*
  `*.forward.pk` name to HTTPS and refuses the click-through on an
  untrusted cert — plain HTTP there is unreachable, not just insecure. And
  Let's Encrypt validates from the public internet, so a LAN-only name can
  never pass HTTP-01. Keeping the internal UIs on `.local` (no HSTS policy)
  sidesteps both without distributing a CA root to every machine.
- **TLS terminates at the company cPanel host, not in the cluster.** The
  office connection does not expose 80 or 443 and will not, so in-cluster
  ACME is impossible: HTTP-01 validates from the public internet on :80, and
  DNS-01 has no cert-manager solver for mysecurecloudhost. cPanel already
  solves it — it owns the public A record, AutoSSL renews the certificate,
  and an `.htaccess` `[P]` rule reverse-proxies inward over a forwarded high
  port. Exactly how `sialkotfootballacademy.pk` has been served all along;
  `hr/labtest/souvenir.forward.pk` use the cruder 301-redirect form that
  leaks `IP:port` into the address bar — don't copy those.
    - `hrms.forward.pk` A record points at the **cPanel host**, not the
      office. Pointing it at the office is what makes the certificate
      unobtainable.
    - **Only the app hostname is published.** The frontend builds with
      `VITE_API_BASE=/api` and its pod's nginx proxies `/api/` to the gateway
      in-cluster, so `api.hrms.forward.pk` stays a LAN name.
    - The prod app IngressRoute matches `PathPrefix(`/`)`, **not** `Host()`.
      `.htaccess` cannot set `ProxyPreserveHost`, so the inbound Host header
      may read `125.209.66.227:5000`; a Host rule 404s every proxied
      request.
    - The cPanel→office hop is **plaintext across the public internet**.
      Firewall the forwarded port to the cPanel host's IP. End-to-end TLS
      would need a DNS-01 cert on a separate `origin.` name — not done.
  `cluster-issuer-acme.yaml` is still applied and still unused; nothing
  references it. Don't wire it back without first making :80 publicly
  reachable, which is the thing that isn't true.
- **The Grafana Alertmanager datasource has no backend plugin.**
  `GET /api/datasources/uid/alertmanager/health` returns
  `{"statusCode":500,"messageId":"plugin.unavailable"}` — that is normal, not
  a fault. It is a frontend-only datasource, so the backend health API does
  not apply. Verify it with the proxy instead:
  `/api/datasources/proxy/uid/alertmanager/api/v2/status` (expect 200 + the
  live Alertmanager config). The other three pinned UIDs do answer `/health`.
- **What's intentionally out of scope.** Alertmanager routing
  (Slack/email), distributed tracing storage swap (Zipkin in-memory is
  fine), Loki retention beyond 7d, Grafana SSO. Add when there's a real
  reason.

## Where things live

- `HIGH_LEVEL_PLAN.md` — architecture, decisions, phasing.
- `docs/api-conventions.md` — HTTP request/response shape.
- `docs/common-lib.md` — `commons` modules.
- `docs/services/<name>.md` — per-service deep dives + schema.
- `docs/server-setup.md` — UAT/Prod single-VM topology.
- `docs/observability.md` — Grafana / Prometheus / Loki / Zipkin operator
  guide.
- `server-setup/` — host scripts (postgres, k3s, jenkins, infra compose),
  JCasC + plugins.txt, k8s platform manifests (cert-manager,
  sealed-secrets, kube-prometheus-stack, Loki, Alloy, Zipkin), ingress,
  env files. Per-service Kustomize bases live under `services/<svc>/k8s/`,
  not here.

Don't introduce new top-level docs without a clear reason.

## Out of scope (don't suggest building)

Mobile BFF, email/SMS/push notifications, generic workflow engine,
multi-tenant, MFA, document service, schema registry, OpenSearch,
Temporal/Axon, Vault, service mesh, GraphQL. Flag any suggestion involving
these as out-of-scope unless I explicitly ask.

## When I bring a new question

Default response shape:
1. One-line direct answer (the recommendation).
2. Two-to-four lines of reasoning.
3. Make the change to the relevant doc/code if it's an obvious follow-up.

If genuinely ambiguous, ask **one** clarifying question.
