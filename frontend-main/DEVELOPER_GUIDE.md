# DEVELOPER_GUIDE.md — FSHRMS Frontend

A practical onboarding for someone landing in this repo for the first time. Read `CLAUDE.md` first for the locked-in rules; this guide tells you **how to actually move** once you have.

## 0. Get the app running

```bash
nvm use 24            # if you use nvm; Node 24, npm 11
npm install
npm run dev           # http://localhost:5173
```

Other commands you'll use daily:

```bash
npm run typecheck     # tsc -b --noEmit — run this before pushing
npm run lint          # eslint — blocks hex literals in components/surfaces
npm run build         # full prod build
```

Backend BFF must be running at `VITE_API_BASE` (defaults to `http://localhost:8080`). Test users sign in with `password123`. For self-service flows that need an employee record, use the seeded "smoke admin with employee attached" (see memory note).

## 1. Mental model — three layers

This is the most important thing to internalize. Everything in `src/` falls into one of three layers, and the layers communicate **top-down only**.

```
┌────────────────────────────────────────────────────────────┐
│  src/routes/_app/**          ← page-level routes           │
│  Scaffold + state wiring + composition. No subcomponents.  │
└────────────────────────────────────────────────────────────┘
                              │ imports from
                              ▼
┌────────────────────────────────────────────────────────────┐
│  src/<module>/<area>/         ← feature module-dirs        │
│  src/attendance/overtime/, src/payroll/runs/, …            │
│  Drawers, tables, cards, hooks for one route family.       │
└────────────────────────────────────────────────────────────┘
                              │ composes
                              ▼
┌────────────────────────────────────────────────────────────┐
│  src/components/**            ← foundation                 │
│  StatCard, DrawerForm, RequestState, PersonCell, …         │
│  Theme/tone-aware. Never knows about a specific feature.   │
└────────────────────────────────────────────────────────────┘
```

Rule of thumb: **if you're writing the same code twice in two routes, push it down a layer.**

## 2. The two questions to ask first

Before you write anything:

1. **Does a foundation component already do this?** — skim `src/components/index.ts` + the "Foundation components — cheat sheet" table in `CLAUDE.md`. We have `RequestState`, `DrawerForm`, `FilterBar`, `KpiGrid`, `EmptyState`, `PersonCell`, `MoneyCell`, `DateRangeBadge`, `StatusPill`, etc.
2. **Does a lib helper already do this?** — `src/lib/` has `dates.ts`, `form-violations.ts`, `api-error.ts`, `use-debounced.ts`, `use-url-drawer-id.ts`, `query.ts`, `http.ts`. Don't reinvent.

If yes — use it. If no — see §6 below before adding anything new.

---

## 3. Adding a new page

Pick the route family the page belongs to. Example: a new "Loan Requests" page under attendance-style approvals.

### Step 1 — create the module-dir

```
src/attendance/loans/
├── index.ts                # barrel
├── LoansTable.tsx
├── ApplyLoanDrawer.tsx
└── LoanStatusPill.tsx      # only if generic StatusPill doesn't cover it
```

The barrel re-exports public symbols:

```ts
// src/attendance/loans/index.ts
export { LoansTable } from './LoansTable';
export { ApplyLoanDrawer } from './ApplyLoanDrawer';
```

Internal API talk goes into the module's `api.ts` / `bff-api.ts` (look at `src/attendance/api.ts` for the shape). Use `http` from `@/lib/http` — it already stamps `Idempotency-Key` on writes.

### Step 2 — create the route file

TanStack Router is file-based. The file path **is** the URL.

`src/routes/_app/attendance/loans.tsx` → `/attendance/loans`.

```tsx
import { createFileRoute } from '@tanstack/react-router';
import { useQuery } from '@tanstack/react-query';
import { PageHeader, KpiGrid, RequestState } from '@/components';
import { LoansTable, ApplyLoanDrawer } from '@/attendance/loans';
import { fetchLoans } from '@/attendance/loans/api';
import { buildAbility } from '@/auth/ability';
import { readAuthSnapshot } from '@/lib/auth-storage';
import { redirect } from '@tanstack/react-router';

export const Route = createFileRoute('/_app/attendance/loans')({
  beforeLoad: () => {
    const snap = readAuthSnapshot();
    const ability = buildAbility(snap?.user);
    if (!ability.can('read', 'attendance.loans')) throw redirect({ to: '/' });
  },
  component: LoansPage,
});

function LoansPage() {
  const q = useQuery({ queryKey: ['loans'], queryFn: fetchLoans });

  return (
    <>
      <PageHeader
        title="Loan requests"
        description="Apply, approve, and track salary advances."
        actions={<ApplyLoanDrawer />}
      />
      <RequestState
        loading={q.isLoading}
        error={q.error}
        empty={q.data?.length === 0}
        errorTitle="Couldn't load loan requests"
        emptyTitle="No loan requests yet"
      >
        <LoansTable rows={q.data ?? []} />
      </RequestState>
    </>
  );
}
```

### Step 3 — regenerate the route tree

```bash
npm run dev   # the Vite plugin regenerates src/routeTree.gen.ts
```

Don't edit `routeTree.gen.ts` by hand. If types complain after rename, restart dev.

### Step 4 — add it to the sidebar

`src/surfaces/shell/Sidebar.tsx` (or its data file) — declare the new item with a `subject` so CASL can filter it. Permissions hide; they don't disable.

### Step 5 — show it on the Dashboard if it's a new foundation pattern

Only if step 1 forced you to introduce a new foundation component — add a `Dashboard.tsx` section so the cross-theme audit catches breakage.

---

## 4. Editing an existing page

The page file at `src/routes/_app/<area>/<page>.tsx` should be small — it's just wiring. Almost every edit you'll make lives in the module-dir.

| You want to… | Edit |
|---|---|
| Change a column, add a column, sort | `src/<module>/<area>/<…>Table.tsx` |
| Change a form field or validation | `src/<module>/<area>/<…>Drawer.tsx` |
| Change KPI tiles | the route file (composition) — they're inline `<KpiGrid><StatCard …/></KpiGrid>` |
| Change page title / description / actions | the route file's `<PageHeader …/>` |
| Change empty / loading / error copy | the route file's `<RequestState errorTitle emptyTitle/>` |
| Change a status chip color | the foundation `StatusPill` tone passed at the call site (don't fork the component) |
| Change date formatting | **never inline.** Add a named helper to `src/lib/dates.ts`, then use it. Naming collisions burned us once. |

If the route is creeping past ~250 lines or growing inline helpers, **extract into the module-dir** before adding more. That's the canonical refactor.

---

## 5. Recipes you'll repeat constantly

### 5a. A query result

```tsx
const q = useQuery({ queryKey: ['leaves', filters], queryFn: () => fetchLeaves(filters) });

<RequestState
  loading={q.isLoading}
  error={q.error}
  empty={q.data?.length === 0}
  errorTitle={forbiddenOr(q.error, "You don't have permission to view leaves", "Couldn't load leaves")}
  emptyTitle="No leave requests"
>
  <LeavesTable rows={q.data ?? []} />
</RequestState>
```

Never hand-roll the `isLoading ? … : isError ? … : empty ? …` ladder.

### 5b. A create/edit drawer with a mutation

```tsx
const [form] = Form.useForm();
const mut = useMutation({
  mutationFn: applyLeave,
  onError: (err) => handleFormError(form, err, message),
  onSuccess: () => { message.success('Leave submitted'); onClose(); qc.invalidateQueries({ queryKey: ['leaves'] }); },
});

<DrawerForm
  title="Apply for leave"
  open={open}
  onClose={onClose}
  form={form}
  submitting={mut.isPending}
  onFinish={(values) => mut.mutate(values)}
>
  <Form.Item name="type" label="Type" rules={[{ required: true }]}><Select … /></Form.Item>
  …
</DrawerForm>
```

`handleFormError` does the inline-field-violation → fallback-message dance for you. Pass `{ fieldMap: { newPassword: 'temporaryPassword' } }` when the server's field name diverges from the form's.

### 5c. A table column

Reach for `src/components/columns.tsx` factories first:

```tsx
import { personColumn, moneyColumn, dateRangeColumn, statusColumn } from '@/components/columns';

const columns = [
  personColumn({ title: 'Employee', dataIndex: 'employee' }),
  moneyColumn({ title: 'Amount', dataIndex: 'amount' }),
  dateRangeColumn({ title: 'Period', startKey: 'startDate', endKey: 'endDate' }),
  statusColumn({ title: 'Status', dataIndex: 'status' }),
];
```

Hand-rolling these renderers means you're now responsible for keeping them in sync with the rest of the app. Don't.

### 5d. A search input

```tsx
const [search, setSearch] = useState('');
const debouncedSearch = useDebounced(search.trim(), 250);
const q = useQuery({ queryKey: ['x', debouncedSearch], queryFn: () => fetchX({ search: debouncedSearch }) });
```

Don't write your own `useEffect + setTimeout` debounce.

### 5e. URL-driven drawer open

```tsx
const { id: openId, open, close } = useUrlDrawerId('leaveId');
```

Drawer state lives in the URL so it survives refresh / share / back-button. Don't keep open/close in `useState` if it's bookmarkable.

---

## 6. Adding something new (component, helper, store)

The bar to add is high. Ask yourself:

| Adding… | Add only if… | Where it goes |
|---|---|---|
| A foundation component in `src/components/` | At least 2 routes need it AND none of AntD's primitives compose to it cleanly | `src/components/<Name>.tsx` + export from `index.ts` + `Dashboard.tsx` section |
| A column renderer | Same shape repeats across ≥2 tables | `src/components/columns.tsx` factory |
| A lib helper in `src/lib/` | Pure, framework-agnostic, ≥2 callers | matching file in `src/lib/`, exported from barrel |
| A new date formatter | A genuinely new output shape, not a rename | `src/lib/dates.ts` with a **distinct** name |
| A Zustand store | State must be triggered from anywhere AND isn't server data AND isn't a single component's | co-locate with the slice (`src/surfaces/shell/palette-store.ts`); lift to `src/stores/` only when ≥3 modules consume it |
| A React context | The shape is "set once at the boundary, read deeply" (auth, ability, theme) — not transient UI | `src/<scope>/<Name>Provider.tsx` |
| A new dependency | AntD genuinely can't do it, and the gap is documented | bump `package.json`, note the gap in `CLAUDE.md` / `PLAN.md` |

If none of those apply: keep it inline or in the module-dir. Three similar lines beats a premature abstraction.

---

## 7. State management — pick the lowest tier

Re-stating the rule from `CLAUDE.md` because this is where new devs reach too high too fast:

1. **Server state → TanStack Query.** Always. Never copy server data into Zustand/Context.
2. **URL state → Router search params.** Filters, active tab, drawer-open-with-id.
3. **Component-local → `useState`.** The default.
4. **Identity-shaped cross-component → Context.** Auth / ability / theme. Already wired.
5. **Triggered-from-anywhere, non-server → Zustand.** Rare. Currently: command palette only.

If you find yourself prop-drilling, the answer is *usually* "lift to URL state" or "both call sites use the same query key" — not "add a context."

---

## 8. Theming & styling — what you're allowed to touch

This trips up everyone once.

| Want to… | Do this | Not this |
|---|---|---|
| Use a brand/AntD color | `const { token } = theme.useToken(); token.colorPrimary` | `var(--ant-color-primary)` (breaks outside AntD's class wrapper) |
| Use an app surface (sidebar bg, accent gradient, spacing scale) | `var(--app-accent-grad-from)`, `var(--app-space-3)` | Hardcoded hex / px |
| Tone-tint a translucent color | `color-mix(in srgb, var(--stat-tone) 45%, transparent)` | `rgba(…)` with a guessed value |
| Add a new theme variable | Edit **both** `src/theme/tokens.ts` (TS contract) **and** `src/styles/themes.css` (per-theme block) | Only one — they must stay in sync |
| Add a hex literal | Only inside `src/theme/themes/<id>.ts` | Anywhere in `src/components/**` or `src/surfaces/**` (ESLint blocks it) |

Iterate themes in real layout: load Dashboard, cycle through all 5 themes via the picker, then adjust. Don't tune palettes in isolation.

---

## 9. Permissions — CASL

Every route that's gated needs a `beforeLoad` guard so direct-URL access bounces:

```ts
beforeLoad: () => {
  const snap = readAuthSnapshot();
  const ability = buildAbility(snap?.user);
  if (!ability.can('read', 'attendance.loans')) throw redirect({ to: '/' });
}
```

In components, hide (don't disable):

```tsx
<Can I="write" a="attendance.loans">
  <Button>Apply</Button>
</Can>
```

Permission strings follow `<service>.<resource>.<verb>`. `write` implies `read`; `admin` implies `manage` (everything).

---

## 10. UI copy — write for users, not developers

Don't surface backend vocabulary in alerts / modals / page descriptions: no Kafka, saga, topic, partition, BFF, gateway, RFC7807, "wishlist." The only exception is the audit operator pages (`/admin/audit/*`) — those tools exist to operate the infrastructure, so the vocabulary is correct there.

Money is on the wire as **rupees** — never convert paisa↔rupees in the frontend. Pass amounts straight to `<MoneyCell>` and through form submits.

---

## 11. Common gotchas

- **`tsc` errors after route rename** — restart `npm run dev`; the route generator needs to re-run.
- **Dark mode text looks dim** — AntD's `darkAlgorithm` derives `colorTextHeading` / `colorTextLabel` / `colorTextDescription` from low-opacity white. Set the whole family in the theme file, not just `colorText`.
- **A `<button>` shows invisible / grayed text** — don't patch locally; `globals.css` has `button { color: inherit }` to fix this once. If it regressed, that's the place.
- **AntD v6 deprecation noise** — use `<Alert title>` (not `message`), `<Drawer size>` (not `width`). For drawers prefer `<DrawerForm>`.
- **A page silently 404s for `/disabilities` or `/banks`** — that's a gateway YAML gap on the backend, not a frontend bug (see memory note).
- **Form errors don't show inline** — you skipped `handleFormError`. Wire it as `onError` on the mutation.
- **You wrote `useMemo` / `useCallback` "just in case"** — React Compiler memoizes for you. The lint rule will warn. Profile before adding manual memo.
- **Same date helper name in two modules** — don't. All date formatters live in `src/lib/dates.ts` with distinct names.

---

## 12. Before you push

```bash
npm run typecheck
npm run lint
```

If you touched a route or added a foundation component, do a 30-second cross-theme pass: cycle Vellum → Klein → Linen → Tokyo Night → Graphite on the page you changed. Most theme regressions are caught instantly that way.

Update `PLAN.md` if you finished a phase / decision; update `DESIGN_SYSTEM.md` if you changed token shape or added a surface. Don't restate things that already live in `CLAUDE.md`.

---

## 13. Where to look when you're stuck

| Question | File |
|---|---|
| What foundation components exist? | `src/components/index.ts` + cheat sheet in `CLAUDE.md` |
| What lib helpers exist? | `src/lib/` (each file is small; just open it) |
| How does an existing route compose itself? | pick any `src/routes/_app/attendance/*.tsx` — they're the canonical shape |
| What does a module-dir look like? | `src/attendance/overtime/` is a good reference (table + apply drawer + bulk drawer + cell renderers + barrel) |
| What endpoints / shapes does the BFF expose? | `docs/services/<service>.openapi.json` (mirrored from backend) |
| What's planned / decided / not built? | `PLAN.md`, `BFF_WISHLIST.md`, `BFF_PAYROLL_WISHLIST.md` |

When in doubt: **search before writing.** The patterns you need almost certainly already exist.
